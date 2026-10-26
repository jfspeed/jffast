/*
 * Copyright 2024-2026 jffast
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.jfspeed.system.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.NotificationStatRes;
import cn.jfspeed.system.entity.NotificationStatVO;
import cn.jfspeed.system.entity.SysNotification;
import cn.jfspeed.system.entity.SysNotificationRecord;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.UserNotificationVO;
import cn.jfspeed.system.mapper.SysNotificationMapper;
import cn.jfspeed.system.mapper.SysNotificationRecordMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import jakarta.annotation.Resource;

/**
 * @author jffast
 */
@Service
@ConditionalOnProperty(name = "jffast.system.notification.enabled", havingValue = "true")
public class SysNotificationRecordService extends BaseService<SysNotificationRecordMapper, SysNotificationRecord> {

    @Resource
    private SysNotificationMapper sysNotificationMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    public PageRes<SysNotificationRecord> page(SysNotificationRecord param, PageReq pageReq) {
        LambdaQueryWrapper<SysNotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(param.getUserId() != null, SysNotificationRecord::getUserId, param.getUserId());
        queryWrapper.eq(param.getNotificationId() != null, SysNotificationRecord::getNotificationId, param.getNotificationId());
        queryWrapper.eq(param.getIsRead() != null, SysNotificationRecord::getIsRead, param.getIsRead());
        queryWrapper.orderByDesc(SysNotificationRecord::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }

    public long getUnreadCount(Long userId) {
        LambdaQueryWrapper<SysNotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotificationRecord::getUserId, userId);
        queryWrapper.eq(SysNotificationRecord::getIsRead, 0);
        return baseMapper.selectCount(queryWrapper);
    }

    public List<UserNotificationVO> getUserNotifications(Long userId) {
        LambdaQueryWrapper<SysNotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotificationRecord::getUserId, userId);
        queryWrapper.orderByDesc(SysNotificationRecord::getCreateTime);
        List<SysNotificationRecord> records = baseMapper.selectList(queryWrapper);

        if (records.isEmpty()) {
            return List.of();
        }

        List<Long> notificationIds = records.stream()
                .map(SysNotificationRecord::getNotificationId)
                .distinct()
                .collect(Collectors.toList());

        List<SysNotification> notifications = sysNotificationMapper.selectBatchIds(notificationIds);
        java.util.Map<Long, SysNotification> notificationMap = notifications.stream()
                .collect(Collectors.toMap(SysNotification::getId, n -> n));

        return records.stream().map(record -> {
            SysNotification notification = notificationMap.get(record.getNotificationId());
            if (notification == null) {
                return null;
            }
            UserNotificationVO vo = new UserNotificationVO();
            vo.setId(notification.getId());
            vo.setTitle(notification.getTitle());
            vo.setContent(notification.getContent());
            vo.setType(notification.getType());
            vo.setLevel(notification.getLevel());
            vo.setSender(notification.getSender());
            vo.setRecipientType(notification.getRecipientType());
            vo.setIsRead(record.getIsRead());
            vo.setSendTime(notification.getSendTime());
            vo.setCreateTime(record.getCreateTime());
            return vo;
        }).filter(vo -> vo != null).collect(Collectors.toList());
    }

    public PageRes<UserNotificationVO> getUserNotificationPage(Long userId, String title, Integer isRead, String type, PageReq pageReq) {
        // 1. 先把 title / type 条件下推成「哪些通知符合」，再用它约束记录查询。
        //    过滤必须发生在分页之前：否则 total 来自未过滤的记录数、list 却已被过滤，
        //    页脚总数与实际条数对不上，翻页还会错位（甚至出现整页空白）。
        List<Long> matchedNotificationIds = null;
        boolean hasFilter = (title != null && !title.isBlank()) || (type != null && !type.isBlank());
        if (hasFilter) {
            LambdaQueryWrapper<SysNotification> notifFilter = new LambdaQueryWrapper<>();
            if (title != null && !title.isBlank()) {
                notifFilter.like(SysNotification::getTitle, title);
            }
            if (type != null && !type.isBlank()) {
                notifFilter.eq(SysNotification::getType, type);
            }
            matchedNotificationIds = sysNotificationMapper.selectList(notifFilter).stream()
                    .map(SysNotification::getId)
                    .collect(Collectors.toList());
            if (matchedNotificationIds.isEmpty()) {
                return new PageRes<>(0L, 0L, List.of());
            }
        }

        // 2. 分页查询记录（title / type 的影响已在这一步之前生效）
        LambdaQueryWrapper<SysNotificationRecord> recordQuery = new LambdaQueryWrapper<>();
        recordQuery.eq(SysNotificationRecord::getUserId, userId);
        if (isRead != null) {
            recordQuery.eq(SysNotificationRecord::getIsRead, isRead);
        }
        if (matchedNotificationIds != null) {
            recordQuery.in(SysNotificationRecord::getNotificationId, matchedNotificationIds);
        }
        recordQuery.orderByDesc(SysNotificationRecord::getCreateTime);

        Page<SysNotificationRecord> recordPage = baseMapper.selectPage(pageReq.getPage(), recordQuery);
        if (recordPage.getTotal() == 0) {
            return new PageRes<>(0L, 0L, List.of());
        }

        List<SysNotificationRecord> records = recordPage.getRecords();
        if (records.isEmpty()) {
            return new PageRes<>(recordPage.getTotal(), 0L, List.of());
        }

        // 3. 查询关联的通知（过滤条件已下推，这里不再需要重复过滤）
        List<Long> notificationIds = records.stream()
                .map(SysNotificationRecord::getNotificationId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<SysNotification> notifQuery = new LambdaQueryWrapper<>();
        notifQuery.in(SysNotification::getId, notificationIds);
        List<SysNotification> notifications = sysNotificationMapper.selectList(notifQuery);
        java.util.Map<Long, SysNotification> notificationMap = notifications.stream()
                .collect(Collectors.toMap(SysNotification::getId, n -> n));

        List<UserNotificationVO> voList = records.stream().map(record -> {
            SysNotification notification = notificationMap.get(record.getNotificationId());
            if (notification == null) {
                return null;
            }
            UserNotificationVO vo = new UserNotificationVO();
            vo.setId(notification.getId());
            vo.setTitle(notification.getTitle());
            vo.setContent(notification.getContent());
            vo.setType(notification.getType());
            vo.setLevel(notification.getLevel());
            vo.setSender(notification.getSender());
            vo.setRecipientType(notification.getRecipientType());
            vo.setIsRead(record.getIsRead());
            vo.setSendTime(notification.getSendTime());
            vo.setCreateTime(record.getCreateTime());
            return vo;
        }).filter(vo -> vo != null).collect(Collectors.toList());

        // total 与 list 现在同源（同一组过滤条件），页脚总数与实际条数一致
        return new PageRes<>(recordPage.getTotal(), recordPage.getPages(), voList);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        LambdaQueryWrapper<SysNotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotificationRecord::getNotificationId, notificationId);
        queryWrapper.eq(SysNotificationRecord::getUserId, userId);
        SysNotificationRecord record = baseMapper.selectOne(queryWrapper);

        if (record == null) {
            throw new cn.jfspeed.common.core.exception.BusinessException("通知记录不存在");
        }
        record.setIsRead(1);
        record.setReadTime(LocalDateTime.now());
        baseMapper.updateById(record);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        SysNotificationRecord update = new SysNotificationRecord();
        update.setIsRead(1);
        update.setReadTime(LocalDateTime.now());
        baseMapper.update(update, new LambdaQueryWrapper<SysNotificationRecord>()
                .eq(SysNotificationRecord::getUserId, userId)
                .eq(SysNotificationRecord::getIsRead, 0));
    }

    public NotificationStatRes getNotificationStats(Long notificationId, String userName, Integer isRead, PageReq pageReq) {
        // 用户名过滤同样必须下推到 SQL：原先在内存里过滤分页后的数据，
        // 于是 total（未过滤）与 list（已过滤）不同源，页脚总数与列表对不上。
        // 先查出匹配的用户 id，再用它约束记录查询（参数化，不拼 SQL 字符串）。
        List<Long> matchedUserIds = null;
        if (userName != null && !userName.isBlank()) {
            LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
            userQuery.and(w -> w.like(SysUser::getUserName, userName)
                    .or().like(SysUser::getRealName, userName));
            matchedUserIds = sysUserMapper.selectList(userQuery).stream()
                    .map(SysUser::getId)
                    .collect(Collectors.toList());
            if (matchedUserIds.isEmpty()) {
                return new NotificationStatRes(0L, 0L, List.of(), 0L, 0L);
            }
        }

        // 1. 统计总数（与列表同源）
        long total = baseMapper.selectCount(buildStatQuery(notificationId, isRead, matchedUserIds));

        if (total == 0) {
            return new NotificationStatRes(0L, 0L, List.of(), 0L, 0L);
        }

        // 2. 分页查询记录
        Page<SysNotificationRecord> recordPage = baseMapper.selectPage(pageReq.getPage(),
                buildStatQuery(notificationId, isRead, matchedUserIds));
        List<SysNotificationRecord> records = recordPage.getRecords();

        // 3. 查询关联用户
        List<Long> userIds = records.stream()
                .map(SysNotificationRecord::getUserId)
                .distinct()
                .collect(Collectors.toList());

        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        java.util.Map<Long, SysUser> userMap = users.stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u));

        List<NotificationStatVO> voList = records.stream().map(record -> {
            SysUser user = userMap.get(record.getUserId());
            return new NotificationStatVO(
                    record.getId(),
                    record.getUserId(),
                    user != null ? user.getUserName() : null,
                    user != null ? user.getRealName() : null,
                    record.getIsRead(),
                    record.getReadTime()
            );
        }).collect(Collectors.toList());

        // 3. 统计已读/未读（与 total 同源：应用用户名过滤，但不受 isRead 过滤影响，
        //    否则 isRead=1 时未读数恒为 0，统计就没有意义了）
        long readCount = baseMapper.selectCount(buildStatQuery(notificationId, 1, matchedUserIds));
        long unreadCount = baseMapper.selectCount(buildStatQuery(notificationId, 0, matchedUserIds));

        return new NotificationStatRes(recordPage.getTotal(), recordPage.getPages(), voList, readCount, unreadCount);
    }

    /**
     * 构造收件记录查询：过滤条件统一走这里，保证 count 与 list 同源。
     *
     * @param matchedUserIds 用户名过滤命中的用户 id；null 表示不过滤用户名
     */
    private LambdaQueryWrapper<SysNotificationRecord> buildStatQuery(Long notificationId, Integer isRead, List<Long> matchedUserIds) {
        LambdaQueryWrapper<SysNotificationRecord> query = new LambdaQueryWrapper<>();
        query.eq(SysNotificationRecord::getNotificationId, notificationId);
        if (isRead != null) {
            query.eq(SysNotificationRecord::getIsRead, isRead);
        }
        if (matchedUserIds != null) {
            query.in(SysNotificationRecord::getUserId, matchedUserIds);
        }
        return query;
    }

    @Transactional
    public void deleteByNotificationId(Long notificationId) {
        LambdaQueryWrapper<SysNotificationRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysNotificationRecord::getNotificationId, notificationId);
        baseMapper.delete(queryWrapper);
    }
}
