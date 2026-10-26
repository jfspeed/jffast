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

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.constant.NotificationConstant;
import cn.jfspeed.system.entity.SysNotification;
import cn.jfspeed.system.entity.SysNotificationRecord;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysNotificationMapper;
import cn.jfspeed.system.notify.NotificationChannelDispatcher;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import cn.jfspeed.web.websocket.WebSocketService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 系统通知服务。
 * <p>
 * 三类推送入口：
 * <ul>
 *   <li>{@link #sendNotification(SysNotification)}：后台「通知管理」发送已编辑的通知，
 *       接收人由 recipientType/recipientIds 解析，与调用方共享事务</li>
 *   <li>{@link #sendToUser} / {@link #sendToUsers}：<b>定向推送</b>，供业务模块、流程引擎等以编程方式调用，
 *       不依赖登录上下文，且运行在<b>独立事务</b>中——通知写失败不会回滚调用方的主业务</li>
 *   <li>{@link #sendToAll}：全员广播</li>
 * </ul>
 * WebSocket 一律按接收人**定向下发**（{@code sendToUsers}），不做无条件广播。
 *
 * @author jffast
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "jffast.system.notification.enabled", havingValue = "true")
public class SysNotificationService extends BaseService<SysNotificationMapper, SysNotification> {

    @Resource
    private SysNotificationRecordService sysNotificationRecordService;

    /**
     * 额外通道（邮件/短信）分发器：站内信固定发送，请求里带 channels 时追加投递。
     */
    @Resource
    private NotificationChannelDispatcher notificationChannelDispatcher;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private SysUserDeptMapper sysUserDeptMapper;

    /**
     * WebSocket 服务可选注入：jffast-starter-web 存在且 {@code jffast.websocket.enabled=true} 时才可用；
     * 未启用时通知仅落库、跳过实时推送，不影响主流程。
     */
    private final ObjectProvider<WebSocketService> webSocketServiceProvider;

    public SysNotificationService(ObjectProvider<WebSocketService> webSocketServiceProvider) {
        this.webSocketServiceProvider = webSocketServiceProvider;
    }

    // ────────────────────────────────────────────────────────────────
    // 查询
    // ────────────────────────────────────────────────────────────────

    public PageRes<SysNotification> page(SysNotification param, PageReq pageReq) {
        LambdaQueryWrapper<SysNotification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(param.getTitle()), SysNotification::getTitle, param.getTitle());
        queryWrapper.eq(StrUtil.isNotBlank(param.getType()), SysNotification::getType, param.getType());
        queryWrapper.eq(StrUtil.isNotBlank(param.getLevel()), SysNotification::getLevel, param.getLevel());
        queryWrapper.eq(StrUtil.isNotBlank(param.getRecipientType()), SysNotification::getRecipientType, param.getRecipientType());
        queryWrapper.eq(param.getStatus() != null, SysNotification::getStatus, param.getStatus());
        queryWrapper.orderByDesc(SysNotification::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }

    // ────────────────────────────────────────────────────────────────
    // 发送（后台「通知管理」）
    // ────────────────────────────────────────────────────────────────

    @Transactional
    public void sendNotification(SysNotification notification) {
        String sender = StrUtil.blankToDefault(LoginUtil.getLoginUserName(), NotificationConstant.DEFAULT_SENDER);

        SysNotification existing = null;
        if (notification.getId() != null) {
            existing = baseMapper.selectById(notification.getId());
        }

        if (existing != null) {
            if (NotificationConstant.STATUS_SENT.equals(existing.getStatus())) {
                throw new BusinessException("该通知已发送，不能重复发送");
            }
            existing.setSender(sender);
            existing.setRecipientType(notification.getRecipientType());
            existing.setRecipientIds(notification.getRecipientIds());
            // 发送通道为瞬态字段，从本次请求带回（existing 只含表字段）
            existing.setChannels(notification.getChannels());
            existing.setStatus(NotificationConstant.STATUS_SENT);
            existing.setSendTime(LocalDateTime.now());
            baseMapper.updateById(existing);
            notification = existing;
        } else {
            notification.setSender(sender);
            notification.setStatus(NotificationConstant.STATUS_SENT);
            notification.setSendTime(LocalDateTime.now());
            applyDefaults(notification);
            baseMapper.insert(notification);
        }

        Set<Long> recipientUserIds = resolveRecipientUserIds(notification);

        createRecords(notification.getId(), recipientUserIds);
        dispatchAfterCommit(recipientUserIds, notification);
    }

    // ────────────────────────────────────────────────────────────────
    // 定向推送（编程式 API，独立事务）
    // ────────────────────────────────────────────────────────────────

    /**
     * 给单个用户推送一条通知（独立事务）。
     *
     * @param userId  接收人用户 ID
     * @param title   标题
     * @param content 内容
     * @param type    类型，见 {@link NotificationConstant}，为空则 system
     * @param level   级别，见 {@link NotificationConstant}，为空则 info
     * @return 通知 ID；接收人为空时返回 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long sendToUser(Long userId, String title, String content, String type, String level) {
        if (userId == null) {
            return null;
        }
        return doSend(Set.of(userId), title, content, type, level, NotificationConstant.DEFAULT_SENDER);
    }

    /**
     * 给一批用户推送同一条通知（独立事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long sendToUsers(Collection<Long> userIds, String title, String content, String type, String level) {
        return doSend(userIds, title, content, type, level, NotificationConstant.DEFAULT_SENDER);
    }

    /**
     * 给一批用户推送同一条通知（独立事务），可指定发送人标识。
     *
     * @param sender 发送人标识（业务模块名 / 流程引擎等），为空则「系统」
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long sendToUsers(Collection<Long> userIds, String title, String content,
                            String type, String level, String sender) {
        return doSend(userIds, title, content, type, level, sender);
    }

    /**
     * 全员广播（独立事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long sendToAll(String title, String content, String type, String level) {
        Set<Long> targets = toUserIdSet(sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().select(SysUser::getId)));
        if (targets.isEmpty()) {
            log.debug("全员广播跳过：系统内无用户，title={}", title);
            return null;
        }
        return doSend(targets, title, content, type, level, NotificationConstant.DEFAULT_SENDER);
    }

    // ────────────────────────────────────────────────────────────────
    // 内部实现
    // ────────────────────────────────────────────────────────────────

    /**
     * 定向推送核心实现：落库 → 建接收记录 → 定向下发 WebSocket。
     */
    private Long doSend(Collection<Long> userIds, String title, String content,
                        String type, String level, String sender) {
        Set<Long> targets = normalizeUserIds(userIds);
        if (targets.isEmpty()) {
            log.debug("定向推送跳过：接收人为空，title={}", title);
            return null;
        }

        SysNotification notification = new SysNotification();
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType(type);
        notification.setLevel(level);
        notification.setSender(sender);
        notification.setRecipientType(NotificationConstant.RECIPIENT_USER);
        notification.setRecipientIds(targets.stream().map(String::valueOf).collect(Collectors.joining(",")));
        notification.setStatus(NotificationConstant.STATUS_SENT);
        notification.setSendTime(LocalDateTime.now());
        applyDefaults(notification);
        baseMapper.insert(notification);

        createRecords(notification.getId(), targets);
        dispatchAfterCommit(targets, notification);
        return notification.getId();
    }

    /**
     * 外部投递（WebSocket / 邮件 / 短信）推迟到事务提交后执行：
     * 避免回滚后接收人已收到通知，也避免在事务内做网络 IO 拉长锁持有时间。
     */
    private void dispatchAfterCommit(Collection<Long> userIds, SysNotification notification) {
        Set<Long> targets = normalizeUserIds(userIds);
        if (targets.isEmpty()) {
            return;
        }
        Runnable dispatch = () -> {
            pushToUsers(targets, notification);
            try {
                notificationChannelDispatcher.dispatch(targets, notification.getTitle(),
                        notification.getContent(), notification.getChannels());
            } catch (Exception e) {
                log.error("通知外部通道投递失败，notificationId={}", notification.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
        } else {
            dispatch.run();
        }
    }

    private void applyDefaults(SysNotification notification) {
        if (StrUtil.isBlank(notification.getType())) {
            notification.setType(NotificationConstant.TYPE_SYSTEM);
        }
        if (StrUtil.isBlank(notification.getLevel())) {
            notification.setLevel(NotificationConstant.LEVEL_INFO);
        }
        if (StrUtil.isBlank(notification.getSender())) {
            notification.setSender(NotificationConstant.DEFAULT_SENDER);
        }
    }

    private Set<Long> normalizeUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void createRecords(Long notificationId, Collection<Long> userIds) {
        if (notificationId == null || userIds == null || userIds.isEmpty()) {
            return;
        }
        List<SysNotificationRecord> records = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            SysNotificationRecord record = new SysNotificationRecord();
            record.setNotificationId(notificationId);
            record.setUserId(userId);
            record.setIsRead(NotificationConstant.UNREAD);
            records.add(record);
        }
        sysNotificationRecordService.saveBatch(records, 500);
    }

    private void pushToUsers(Collection<Long> userIds, SysNotification notification) {
        WebSocketService webSocketService = webSocketServiceProvider.getIfAvailable();
        if (webSocketService == null) {
            return;
        }
        Set<Long> targets = normalizeUserIds(userIds);
        if (targets.isEmpty()) {
            return;
        }
        try {
            webSocketService.sendToUsers(targets, buildMessage(notification));
        } catch (Exception e) {
            log.warn("发送WebSocket通知失败，notificationId={}", notification.getId(), e);
        }
    }

    private String buildMessage(SysNotification notification) {
        Map<String, Object> msg = new HashMap<>(4);
        msg.put("type", "notification");
        msg.put("data", notification);
        return JSONUtil.toJsonStr(msg);
    }

    /**
     * 解析通知的接收人 ID 集合。
     * <p>
     * recipientType 为空或 all → 全体用户；role/dept → 关联表解析；user → recipientIds 直接作为用户 ID。
     * 只查询 id 列，避免把整张用户表加载进内存。
     */
    private Set<Long> resolveRecipientUserIds(SysNotification notification) {
        String recipientType = notification.getRecipientType();
        if (StrUtil.isBlank(recipientType) || NotificationConstant.RECIPIENT_ALL.equals(recipientType)) {
            return toUserIdSet(sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().select(SysUser::getId)));
        }

        String recipientIds = notification.getRecipientIds();
        if (StrUtil.isBlank(recipientIds)) {
            return new LinkedHashSet<>();
        }

        Set<Long> idSet = parseRecipientIds(recipientIds);

        if (idSet.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Long> userIds;
        switch (recipientType) {
            case NotificationConstant.RECIPIENT_ROLE:
                List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, idSet));
                userIds = userRoles.stream().map(SysUserRole::getUserId).collect(Collectors.toSet());
                break;
            case NotificationConstant.RECIPIENT_DEPT:
                List<SysUserDept> userDepts = sysUserDeptMapper.selectList(
                        new LambdaQueryWrapper<SysUserDept>().in(SysUserDept::getDeptId, idSet));
                userIds = userDepts.stream().map(SysUserDept::getUserId).collect(Collectors.toSet());
                break;
            case NotificationConstant.RECIPIENT_USER:
                userIds = idSet;
                break;
            default:
                return new LinkedHashSet<>();
        }

        if (userIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        return toUserIdSet(sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId)
                .in(SysUser::getId, userIds)));
    }

    private Set<Long> toUserIdSet(List<SysUser> users) {
        return users.stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 解析逗号分隔的接收人 ID；非数字项视为非法输入返回 400，而不是抛 NumberFormatException 变 500。
     */
    private Set<Long> parseRecipientIds(String recipientIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String raw : recipientIds.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException e) {
                throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "接收人 ID 格式不正确：" + value);
            }
        }
        return ids;
    }

    public SysNotification copyNotification(Long id) {
        SysNotification source = baseMapper.selectById(id);
        if (source == null) {
            throw new BusinessException("通知不存在");
        }
        SysNotification copy = new SysNotification();
        copy.setTitle(source.getTitle() + " - 副本");
        copy.setContent(source.getContent());
        copy.setType(source.getType());
        copy.setLevel(source.getLevel());
        copy.setRecipientType(source.getRecipientType());
        copy.setRecipientIds(source.getRecipientIds());
        copy.setStatus(NotificationConstant.STATUS_UNSENT);
        baseMapper.insert(copy);
        return copy;
    }
}
