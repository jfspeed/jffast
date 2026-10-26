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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.domain.dto.SysLogExcel;
import cn.jfspeed.system.domain.dto.SysLogQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.mapper.SysLogMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/**
 * @author jffast
 */
@Service
public class SysLogService extends BaseService<SysLogMapper, SysLog>{

    @Resource
    private SysUserMapper sysUserMapper;

    public PageRes<SysLog> page(SysLogQueryRequest sysLog, PageReq pageQuery){
        LambdaQueryWrapper<SysLog> queryWrapper = buildQueryWrapper(sysLog);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageQuery.getPage(),queryWrapper));
    }

    private LambdaQueryWrapper<SysLog> buildQueryWrapper(SysLogQueryRequest sysLog) {
        LambdaQueryWrapper<SysLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(sysLog.getTitle()),SysLog::getTitle,sysLog.getTitle());
        if (StrUtil.isNotBlank(sysLog.getCreateByName())) {
            LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
            userQuery.like(SysUser::getUserName, sysLog.getCreateByName());
            userQuery.select(SysUser::getId);
            List<SysUser> users = sysUserMapper.selectList(userQuery);
            if (users.isEmpty()) {
                queryWrapper.eq(SysLog::getCreateBy, -1);
            } else {
                queryWrapper.in(SysLog::getCreateBy, users.stream().map(SysUser::getId).toList());
            }
        }
        queryWrapper.like(StrUtil.isNotBlank(sysLog.getIp()),SysLog::getIp,sysLog.getIp());
        queryWrapper.eq(StrUtil.isNotBlank(sysLog.getState()),SysLog::getState,sysLog.getState());
        queryWrapper.eq(StrUtil.isNotBlank(sysLog.getLevel()),SysLog::getLevel,sysLog.getLevel());
        queryWrapper.like(StrUtil.isNotBlank(sysLog.getRequestUri()),SysLog::getRequestUri,sysLog.getRequestUri());
        queryWrapper.le(StrUtil.isNotBlank(sysLog.getEndTime()),SysLog::getCreateTime,sysLog.getEndTime());
        queryWrapper.ge(StrUtil.isNotBlank(sysLog.getStartTime()),SysLog::getCreateTime,sysLog.getStartTime());

        queryWrapper.orderByDesc(SysLog::getCreateTime);
        return queryWrapper;
    }

    /**
     * 导出用列表：与分页同一套过滤条件，行数上限 maxRows 防止一次导出拖垮内存
     */
    public List<SysLogExcel> listForExport(SysLogQueryRequest sysLog, int maxRows) {
        LambdaQueryWrapper<SysLog> queryWrapper = buildQueryWrapper(sysLog);
        queryWrapper.last("limit " + maxRows);
        List<SysLog> list = baseMapper.selectList(queryWrapper);

        // 操作人名称批量反查（create_by → user_name）
        Map<Long, String> userNames = new HashMap<>();
        List<Long> userIds = list.stream().map(SysLog::getCreateBy).filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            LambdaQueryWrapper<SysUser> userQuery = new LambdaQueryWrapper<>();
            userQuery.in(SysUser::getId, userIds);
            userQuery.select(SysUser::getId, SysUser::getUserName);
            for (SysUser user : sysUserMapper.selectList(userQuery)) {
                userNames.put(user.getId(), user.getUserName());
            }
        }

        return list.stream().map(log -> {
            SysLogExcel excel = new SysLogExcel();
            excel.setTitle(log.getTitle());
            excel.setCreateByName(log.getCreateBy() == null ? null : userNames.get(log.getCreateBy()));
            excel.setMethod(log.getMethod());
            excel.setRequestUri(log.getRequestUri());
            excel.setIp(log.getIp());
            // sys_log.state 实际落库值为「成功/失败」文本（LogAspect 写入）
            excel.setStateText(log.getState());
            excel.setLevel(log.getLevel());
            excel.setDuration(log.getDuration());
            excel.setInfo(log.getInfo());
            excel.setCreateTime(log.getCreateTime());
            return excel;
        }).toList();
    }

    public List<String> titleList(){
        return baseMapper.selectTitleList();
    }

    /**
     * 按天统计日志趋势（总数 / 成功数）。
     */
    public List<Map<String, Object>> dailyTrend(LocalDateTime start) {
        return baseMapper.selectDailyTrend(start);
    }

    /**
     * 清理指定天数前的日志
     */
    public int cleanLogs(int days) {
        if (days < 1) {
            // minusDays(0)/minusDays(负数) 会命中全部日志，属于不可恢复的误删
            throw new BusinessException("清理天数必须大于 0");
        }
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        return baseMapper.delete(new LambdaQueryWrapper<SysLog>()
                .lt(SysLog::getCreateTime, before));
    }
}
