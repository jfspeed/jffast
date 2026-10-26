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
package cn.jfspeed.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/dashboard")
@Tag(name = "仪表盘")
public class SysDashboardController extends BaseController<SysUserService, SysUser> {

    @Resource
    private SysLogService sysLogService;

    @GetMapping("/stats")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "获取仪表盘统计数据")
    public R stats() {
        Map<String, Object> stats = new HashMap<>();

        long userCount = service.count();
        long todayLogCount = getTodayLogCount();
        long successLogCount = getSuccessLogCount();
        long failLogCount = getFailLogCount();

        stats.put("userCount", userCount);
        stats.put("todayLogCount", todayLogCount);
        stats.put("successLogCount", successLogCount);
        stats.put("failLogCount", failLogCount);

        return R.success(stats);
    }

    @GetMapping("/logTrend")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "获取最近7天操作日志趋势")
    public R logTrend() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);
        Map<String, long[]> grouped = new HashMap<>();
        for (Map<String, Object> row : sysLogService.dailyTrend(startDate.atStartOfDay())) {
            Object date = row.get("d");
            if (date == null) {
                continue;
            }
            grouped.put(String.valueOf(date), new long[]{toLong(row.get("total")), toLong(row.get("succ"))});
        }

        List<Map<String, Object>> trend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            long[] values = grouped.getOrDefault(date, new long[]{0L, 0L});
            Map<String, Object> item = new HashMap<>(3);
            item.put("date", date);
            item.put("count", values[0]);
            item.put("successCount", values[1]);
            trend.add(item);
        }
        return R.success(trend);
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @GetMapping("/recentLogs")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "获取最近操作日志")
    public R recentLogs() {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        qw.last("LIMIT 10");
        List<SysLog> logs = sysLogService.list(qw);
        return R.success(logs);
    }

    private long getTodayLogCount() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = LocalDate.now().atTime(LocalTime.MAX);
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.ge("create_time", start);
        qw.le("create_time", end);
        return sysLogService.count(qw);
    }

    private long getSuccessLogCount() {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.eq("state", "成功");
        return sysLogService.count(qw);
    }

    private long getFailLogCount() {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.eq("state", "失败");
        return sysLogService.count(qw);
    }
}
