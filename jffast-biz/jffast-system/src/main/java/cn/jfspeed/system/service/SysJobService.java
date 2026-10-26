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
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.scheduler.JobScheduler;
import cn.jfspeed.job.util.CronUtil;
import cn.jfspeed.system.domain.dto.SysJobQueryRequest;
import cn.jfspeed.system.entity.SysJob;
import cn.jfspeed.system.entity.SysJobLog;
import cn.jfspeed.system.mapper.SysJobMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 定时任务管理（页面 / API 侧）。
 *
 * <p>调度**不由本类发起**：
 * <ul>
 *   <li>调度能力在 `jffast-starter-job` 的 {@link JobScheduler}，**任何服务**引入该 Starter
 *       就能拥有自己归属的任务；本类只做"任务元数据的增删改查"；</li>
 *   <li>任务的**归属**由 `sys_job.service_name` 决定，只有归属服务会执行它，
 *       避免多服务共享一张任务表时重复执行；</li>
 *   <li>页面上的启用/暂停/改 cron 只需写库 + 调一次 {@link JobScheduler#sync()}；
 *       **不归本服务的任务**会在其归属服务的下次周期同步（默认 30s）自动生效，
 *       不需要去重启那个服务。</li>
 * </ul>
 *
 * <p>{@code @ConditionalOnProperty}：`jffast.job.enabled=false` 时本类与其 Controller 一起缺席，
 * 这样"关掉定时任务能力"不会因缺少 Starter 的 Bean 而让服务启动失败。
 *
 * @author jffast
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "jffast.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SysJobService extends BaseService<SysJobMapper, SysJob> {

    @Resource
    private JobScheduler jobScheduler;

    @Resource
    private SysJobLogService sysJobLogService;

    /**
     * 新增任务。
     * <p>注解（`@JfFastJob`）是任务的主来源，这里保留"页面手工建任务"的老能力：
     * 归属服务留空时默认 = **本服务** —— 因为页面上的"调用目标"下拉
     * （`GET /sys/job/targets`）列出的只是**本服务**声明的任务方法，Bean 在本服务里，自然由本服务执行。
     */
    public void createJob(SysJob job) {
        CronUtil.validate(job.getCronExpression());
        if (StrUtil.isBlank(job.getServiceName())) {
            job.setServiceName(jobScheduler.getAppName());
        }
        save(job);
        jobScheduler.sync();
        log.info("【定时任务】新增任务 {}（归属服务={}，状态={}）", job.getJobName(), job.getServiceName(), job.getStatus());
    }

    public void updateJob(SysJob job) {
        if (StrUtil.isNotBlank(job.getCronExpression())) {
            CronUtil.validate(job.getCronExpression());
        }
        // 与 createJob 保持一致：页面「执行服务」留空 = 归属**本服务**（页面上能选的调用目标只有本服务的 Bean）。
        // 否则留空会把归属清成 NULL ⇒ 被"默认调度者"接管，与页面文案/直觉都不符（2026-09-24 评审整改）。
        if (StrUtil.isBlank(job.getServiceName())) {
            job.setServiceName(jobScheduler.getAppName());
        }
        SysJob old = getById(job.getId());
        updateById(job);
        // ⚠️ 改名 / 改组 ⇒ Quartz 的 JobKey 变了，旧 Trigger 不会自己消失（会按老 cron 继续幽灵执行）。
        //    这里先按**旧** name+group 主动清一次；手工改库等其它路径由 sync() 的反向对账兜底。
        if (old != null && (!StrUtil.equals(old.getJobName(), job.getJobName())
                || !StrUtil.equals(old.getJobGroup(), job.getJobGroup()))) {
            log.info("【定时任务】任务改名/改组，清理旧触发器: {}.{} → {}.{}",
                    old.getJobGroup(), old.getJobName(), job.getJobGroup(), job.getJobName());
            jobScheduler.cancel(old.getJobName(), old.getJobGroup());
        }
        // 归属 / 启停 / cron 的变更都由 sync() 统一落地（含"归属被改到别的服务时清掉本进程触发器"）
        jobScheduler.sync();
    }

    public void deleteJob(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            return;
        }
        jobScheduler.cancel(job.getJobName(), job.getJobGroup());
        removeById(id);
        // 反向对账兜底：即使上面的 cancel 失败（Quartz 偶发异常），sync() 也会因为"数据库里已无此行"
        // 把触发器清掉 —— 不会再出现"任务删了、触发器还在跑"的幽灵任务。
        jobScheduler.sync();
    }

    public void pauseJob(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            return;
        }
        job.setStatus(0);
        updateById(job);
        // 非本服务归属的任务：其归属服务会在下个周期同步里看到 status=0 并停止调度（无需重启）
        jobScheduler.sync();
    }

    public void resumeJob(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            return;
        }
        job.setStatus(1);
        updateById(job);
        jobScheduler.sync();
    }

    /**
     * 立即执行一次。
     * <p>⚠️ 只有**归属本服务**的任务才能在这里触发 —— 任务方法是被反射调用的 Bean，
     * 而本服务并不一定有那个 Bean（例如示例任务归属 demo 模块时）。
     * 此时给出明确提示，而不是抛 `NoSuchBeanDefinitionException`。
     */
    public void triggerJob(Long id) {
        SysJob job = getById(id);
        if (job == null) {
            return;
        }
        JobRecord record = toRecord(job);
        if (!jobScheduler.canRunHere(record)) {
            throw new BusinessException("任务「" + job.getJobName() + "」归属 " + jobScheduler.describeOwner(record)
                    + "，请在该服务上触发（本服务没有它的 Bean）");
        }
        try {
            jobScheduler.triggerNow(record);
        } catch (Exception e) {
            // Starter 不能依赖 jffast-common（不抛业务异常），这里统一转换成用户可读的业务异常，
            // 否则会被全局兜底 handler 吞成 500「系统繁忙」，页面看不到真正原因。
            throw new BusinessException("立即执行失败：" + e.getMessage());
        }
    }

    public boolean checkCron(String cronExpression) {
        return CronUtil.isValid(cronExpression);
    }

    public String getNextExecution(String cronExpression) {
        java.util.Date next = CronUtil.getNextExecution(cronExpression);
        return next != null ? next.toString() : null;
    }

    public PageRes<SysJob> page(SysJobQueryRequest query, PageReq pageReq) {
        LambdaQueryWrapper<SysJob> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getJobName()), SysJob::getJobName, query.getJobName());
        queryWrapper.eq(StrUtil.isNotBlank(query.getJobGroup()), SysJob::getJobGroup, query.getJobGroup());
        queryWrapper.eq(query.getStatus() != null, SysJob::getStatus, query.getStatus());
        queryWrapper.orderByDesc(SysJob::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }

    /**
     * 分页查询任务执行日志
     */
    public PageRes<SysJobLog> pageJobLog(Long jobId, Integer pageNo, Integer pageSize) {
        LambdaQueryWrapper<SysJobLog> queryWrapper = new LambdaQueryWrapper<>();
        if (jobId != null) {
            SysJob job = getById(jobId);
            if (job != null) {
                queryWrapper.eq(SysJobLog::getJobName, job.getJobName());
            }
        }
        queryWrapper.orderByDesc(SysJobLog::getCreateTime);
        return PageUtil.resultPageMp(sysJobLogService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNo, pageSize), queryWrapper));
    }

    private JobRecord toRecord(SysJob job) {
        JobRecord record = new JobRecord();
        record.setId(job.getId());
        record.setJobName(job.getJobName());
        record.setJobGroup(job.getJobGroup());
        record.setInvokeTarget(job.getInvokeTarget());
        record.setCronExpression(job.getCronExpression());
        record.setMisfirePolicy(job.getMisfirePolicy());
        record.setConcurrent(job.getConcurrent());
        record.setStatus(job.getStatus());
        record.setServiceName(job.getServiceName());
        return record;
    }
}
