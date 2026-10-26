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

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.Valid;
import cn.jfspeed.system.domain.dto.SysJobRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.job.domain.JobTargetInfo;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.domain.dto.SysJobQueryRequest;
import cn.jfspeed.system.entity.SysJob;
import cn.jfspeed.system.service.SysJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/sys/job")
@Tag(name = "定时任务管理")
// 与 SysJobService 同一开关：jffast.job.enabled=false 时本 Controller 一起缺席，
// 避免"关掉定时任务能力"却因缺少 Starter 的 Bean 而导致服务启动失败（旧实现有这个坑）。
/**
 * @author jffast
 */
@ConditionalOnProperty(prefix = "jffast.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SysJobController extends BaseController<SysJobService, SysJob> {

    private final JobAnnotationScanner jobAnnotationScanner;

    public SysJobController(SysJobService service, JobAnnotationScanner jobAnnotationScanner) {
        this.service = service;
        this.jobAnnotationScanner = jobAnnotationScanner;
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "定时任务列表")
    public R page(SysJobQueryRequest query, PageReq pageReq) {
        return R.success(service.page(query, pageReq));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "定时任务详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增定时任务")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增定时任务")
    public R create(@RequestBody @Valid SysJobRequest req) {
        SysJob job = BeanUtil.copyProperties(req, SysJob.class);
        service.createJob(job);
        return R.success();
    }

    @SLog(title = "修改定时任务")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:update")
    @DistributedLock(key = "'job:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改定时任务")
    public R update(@RequestBody @Valid SysJobRequest req) {
        SysJob job = BeanUtil.copyProperties(req, SysJob.class);
        service.updateJob(job);
        return R.success();
    }

    @SLog(title = "删除定时任务")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:delete")
    @Operation(summary = "删除定时任务")
    public R delete(@PathVariable("id") Long id) {
        service.deleteJob(id);
        return R.success();
    }

    @SLog(title = "暂停定时任务")
    @PutMapping("/pause/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:update")
    @Operation(summary = "暂停定时任务")
    public R pause(@PathVariable("id") Long id) {
        service.pauseJob(id);
        return R.success();
    }

    @SLog(title = "恢复定时任务")
    @PutMapping("/resume/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:update")
    @Operation(summary = "恢复定时任务")
    public R resume(@PathVariable("id") Long id) {
        service.resumeJob(id);
        return R.success();
    }

    @SLog(title = "立即执行定时任务")
    @PutMapping("/trigger/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:trigger")
    @Operation(summary = "立即执行定时任务")
    public R trigger(@PathVariable("id") Long id) {
        service.triggerJob(id);
        return R.success();
    }

    @GetMapping("/checkCron")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "校验CRON表达式")
    public R checkCron(@RequestParam("cronExpression") String cronExpression) {
        return R.success(service.checkCron(cronExpression));
    }

    @GetMapping("/nextExecution")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "获取下次执行时间")
    public R nextExecution(@RequestParam("cronExpression") String cronExpression) {
        return R.success(service.getNextExecution(cronExpression));
    }

    @GetMapping("/targets")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "获取可用的调用目标列表")
    public R targets() {
        List<JobTargetInfo> targets = jobAnnotationScanner.getJobTargets();
        return R.success(targets);
    }

    // 任务执行日志的分页查询统一由 SysJobLogController 提供：
    //   GET /sys/job/log/page  —— 对应前端 jobLog 的 createCrud('/sys/job/log')
    // 此处曾另有一个 @GetMapping("/log/page") 的重复实现，两个控制器映射到**同一路径**
    // （/sys/job + /log/page 与 /sys/job/log + /page 完全相同），
    // 导致 Spring 启动时抛 "Ambiguous mapping. Cannot map 'sysJobLogController' ...
    // to {GET [/sys/job/log/page]}: There is already 'sysJobController' bean method ... mapped"，
    // **应用完全无法启动**（运行时才暴露，编译期完全看不出来）。
    // 该重复实现无人调用（前端用的是 SysJobLogController 那条），故删除。
}
