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

import cn.dev33.satoken.stp.StpUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.security.config.InternalCallDetector;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.system.service.SysUserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘内部接口（供服务间调用的示例数据源，数据口径与 {@link SysDashboardController} 一致）。
 *
 * <h3>为什么这里没有 {@code @SaCheckPermission}</h3>
 * Sa-Token 的注解鉴权要求先有登录会话，而分布式形态下的<b>服务间调用</b>（内部验签透传）
 * 按设计<b>没有</b> Sa-Token 会话。给这类目标端点加注解，会让每一次服务间调用都返回
 * 401「登录失效」——曾经踩过一次：{@code /sys/dashboard/stats} 补上注解后，
 * 内部调用被重新挡在门外。故本控制器的授权语义改由
 * {@link #requireInternalOrLogPermission()} 显式补齐。
 *
 * <h3>安全边界</h3>
 * <ol>
 *   <li><b>不在浏览器可路由前缀下</b>：路径是 {@code /internal/**}，网关只路由
 *       {@code /api/sys/**} 等业务前缀 —— 浏览器经网关拿不到本控制器。此前它挂在
 *       {@code /sys/dashboard/inner/**} 下，而网关会给每个已登录用户的请求签名，
 *       「已验签内部调用」因此无法区分浏览器与服务间调用（越权读全量日志的口子）。</li>
 *   <li><b>未登录的直连请求</b>：进入控制器之前 {@code SaInterceptor} 已经执行
 *       {@code StpUtil.checkLogin()}，直接 401。</li>
 *   <li><b>伪造 {@code X-User-Id} / {@code X-User-Sign} 的请求</b>：{@code UserContextFilter}
 *       用内部共享密钥验签，签名对不上等于匿名，与「未登录」同一条路。</li>
 *   <li><b>已验签的内部调用</b>（服务间透传）：放行。</li>
 *   <li><b>已登录的非内部调用</b>（单体直连 / 绕过网关直连服务端口）：按浏览器侧同口径
 *       校验超管角色或 {@code system:log:view} 权限，不再「登录即可读全量日志」。</li>
 * </ol>
 *
 * @author jffast
 */
@RestController
@RequestMapping("/internal/dashboard")
@Tag(name = "仪表盘内部接口")
public class SysDashboardInnerController {

    @Resource
    private SysUserService userService;

    @Resource
    private SysLogService sysLogService;

    /** 内部调用验签能力由分布式形态的实现方注入；单体型无实现，getIfAvailable() 为 null。 */
    @Resource
    private ObjectProvider<InternalCallDetector> internalCallDetector;

    /**
     * 已验签的服务间调用直接放行；其余请求（浏览器/直连）必须具备仪表盘同款权限。
     */
    private void requireInternalOrLogPermission() {
        InternalCallDetector detector = internalCallDetector.getIfAvailable();
        if (detector != null && detector.isTrustedInternalCall()) {
            return;
        }
        if (!StpUtil.hasRole(BaseConstant.ROLE_SUPER_ADMIN) && !StpUtil.hasPermission("system:log:view")) {
            StpUtil.checkPermission("system:log:view");
        }
    }

    @GetMapping("/stats")
    @Operation(summary = "获取仪表盘统计数据（服务间内部接口）")
    public R innerStats() {
        requireInternalOrLogPermission();
        Map<String, Object> stats = new HashMap<>();

        long userCount = userService.count();
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
    @Operation(summary = "获取最近7天操作日志趋势（服务间内部接口）")
    public R innerLogTrend() {
        requireInternalOrLogPermission();
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            QueryWrapper<SysLog> qw = new QueryWrapper<>();
            qw.ge("create_time", start);
            qw.le("create_time", end);
            long count = sysLogService.count(qw);

            QueryWrapper<SysLog> successQw = new QueryWrapper<>();
            successQw.ge("create_time", start);
            successQw.le("create_time", end);
            successQw.eq("state", "成功");
            long successCount = sysLogService.count(successQw);

            Map<String, Object> item = new HashMap<>();
            item.put("date", date.toString());
            item.put("count", count);
            item.put("successCount", successCount);
            trend.add(item);
        }
        return R.success(trend);
    }

    @GetMapping("/recentLogs")
    @Operation(summary = "获取最近操作日志（服务间内部接口）")
    public R innerRecentLogs() {
        requireInternalOrLogPermission();
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
