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
package cn.jfspeed.job.manager;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;

/**
 * @author jffast
 */
public class QuartzManager {

    private final Scheduler scheduler;

    public QuartzManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void createJob(String jobName, String jobGroup, String cronExpression,
                          Class<? extends Job> jobClass, Map<String, Object> params) throws SchedulerException {
        createJob(jobName, jobGroup, cronExpression, jobClass, params, null);
    }

    /**
     * 创建/重建任务触发器。
     *
     * @param misfirePolicy `sys_job.misfire_policy`：1=立即执行（补跑一次）2=放弃错过的执行 3=下次执行（默认）
     */
    public void createJob(String jobName, String jobGroup, String cronExpression,
                          Class<? extends Job> jobClass, Map<String, Object> params,
                          String misfirePolicy) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            deleteJob(jobName, jobGroup);
        }
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .storeDurably()
                .build();
        if (params != null) {
            params.forEach((key, value) -> jobDetail.getJobDataMap().put(key, value));
        }
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName, jobGroup)
                .forJob(jobDetail)
                .withSchedule(buildCronSchedule(cronExpression, misfirePolicy))
                .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    /**
     * 按 `misfire_policy` **真正**构造错过触发（misfire）时的策略。
     *
     * <p>⚠️ 2026-09-23 修（既有缺陷）：本方法此前**恒用** `DoNothing`，导致 `sys_job.misfire_policy`
     * 这个字段形同虚设 —— 而 `DoNothing` 在"错过触发"时会**丢弃**这次执行，
     * 表现为"任务配好了、日志里却有 `Handling N trigger(s) that missed their scheduled fire-time`、
     * 而且 `QRTZ_TRIGGERS.PREV_FIRE_TIME` 一直为空"（单体 JDBC jobstore 实测）。
     */
    private CronScheduleBuilder buildCronSchedule(String cronExpression, String misfirePolicy) {
        CronScheduleBuilder builder = CronScheduleBuilder.cronSchedule(cronExpression);
        String policy = misfirePolicy == null ? "3" : misfirePolicy.trim();
        switch (policy) {
            case "1" -> builder.withMisfireHandlingInstructionFireAndProceed();
            case "2" -> builder.withMisfireHandlingInstructionIgnoreMisfires();
            default -> builder.withMisfireHandlingInstructionDoNothing();
        }
        return builder;
    }

    public void pauseJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.pauseJob(JobKey.jobKey(jobName, jobGroup));
    }

    public void resumeJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.resumeJob(JobKey.jobKey(jobName, jobGroup));
    }

    public void deleteJob(String jobName, String jobGroup) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
        scheduler.pauseTrigger(triggerKey);
        scheduler.unscheduleJob(triggerKey);
        scheduler.deleteJob(jobKey);
    }

    public void triggerJob(String jobName, String jobGroup) throws SchedulerException {
        scheduler.triggerJob(JobKey.jobKey(jobName, jobGroup));
    }

    /**
     * 确保 JobDetail 存在但**不排期**（`storeDurably`），供"立即执行一次"使用 ——
     * 这样对一个"已暂停 / 尚未排期"的任务也能手工触发，而无需先给它编一个 cron。
     *
     * <p>⚠️ 已存在时**必须把最新的 JobDataMap 写回**：JobDataMap 里存的是 `invokeTarget`
     * （真正被调用的 bean.method）。若只在"不存在"时写入，那么"改了执行目标"后点「立即执行」
     * 仍然会跑**旧目标**（触发器重建前留下的历史数据），表现为"改了没反应"。
     */
    public void ensureJobExists(String jobName, String jobGroup, Class<? extends Job> jobClass,
                                Map<String, Object> params) throws SchedulerException {
        JobKey jobKey = JobKey.jobKey(jobName, jobGroup);
        if (scheduler.checkExists(jobKey)) {
            refreshJobDataMap(jobKey, params);
            return;
        }
        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(jobName, jobGroup)
                .storeDurably()
                .build();
        if (params != null) {
            params.forEach((key, value) -> jobDetail.getJobDataMap().put(key, value));
        }
        scheduler.addJob(jobDetail, true);
    }

    /**
     * 把最新的 JobDataMap 写回已存在的 JobDetail（只改值变化了的键）。
     *
     * <p>{@code addJob(jobDetail, true)} 只替换 JobDetail，**不影响已挂在其上的触发器** ——
     * 所以「立即执行」能立刻用上新目标，而定时排期不受干扰。
     */
    private void refreshJobDataMap(JobKey jobKey, Map<String, Object> params) throws SchedulerException {
        if (params == null || params.isEmpty()) {
            return;
        }
        JobDetail existing = scheduler.getJobDetail(jobKey);
        if (existing == null) {
            return;
        }
        boolean changed = params.entrySet().stream()
                .anyMatch(e -> !Objects.equals(e.getValue(), existing.getJobDataMap().get(e.getKey())));
        if (!changed) {
            return;
        }
        existing.getJobDataMap().putAll(params);
        scheduler.addJob(existing, true);
    }

    /**
     * 取本进程当前任务写入 JobDataMap 的值（没有该 JobDetail 时返回 null）。
     * 供调度对账判断"调用目标是否变过"。
     */
    public Object getJobDataValue(String jobName, String jobGroup, String key) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(jobName, jobGroup));
        if (jobDetail == null) {
            return null;
        }
        return jobDetail.getJobDataMap().get(key);
    }

    /**
     * 取本进程当前触发器的失火策略，换算回 {@code sys_job.misfire_policy} 的口径
     * （1=立即执行 2=放弃错过的执行 3=下次执行）；没有该触发器时返回 null。
     *
     * <p>口径对应（与 {@link #buildCronSchedule} 一一对应）：
     * <ul>
     *   <li>"1" → {@code MISFIRE_INSTRUCTION_FIRE_ONCE_NOW}(1)</li>
     *   <li>"2" → {@code MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY}(-1)</li>
     *   <li>"3" → {@code MISFIRE_INSTRUCTION_DO_NOTHING}(2)</li>
     * </ul>
     *
     * <p>失火策略是**触发器级**属性，JDBC jobstore 里无法就地修改，只能重建触发器 ——
     * 所以对账时必须能读出当前值，才能判断"策略是否变过"。
     */
    public String getMisfirePolicy(String jobName, String jobGroup) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(jobName, jobGroup));
        if (!(trigger instanceof CronTrigger cronTrigger)) {
            return null;
        }
        return switch (cronTrigger.getMisfireInstruction()) {
            case CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW -> "1";
            case Trigger.MISFIRE_INSTRUCTION_IGNORE_MISFIRE_POLICY -> "2";
            default -> "3";
        };
    }

    public void updateJobCron(String jobName, String jobGroup, String cronExpression) throws SchedulerException {
        TriggerKey triggerKey = TriggerKey.triggerKey(jobName, jobGroup);
        CronTrigger trigger = (CronTrigger) scheduler.getTrigger(triggerKey);
        if (trigger != null && !trigger.getCronExpression().equalsIgnoreCase(cronExpression)) {
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(cronExpression)
                    .withMisfireHandlingInstructionDoNothing();
            trigger = trigger.getTriggerBuilder()
                    .withIdentity(triggerKey)
                    .withSchedule(scheduleBuilder)
                    .build();
            scheduler.rescheduleJob(triggerKey, trigger);
        }
    }

    public boolean checkExists(String jobName, String jobGroup) throws SchedulerException {
        return scheduler.checkExists(JobKey.jobKey(jobName, jobGroup));
    }

    /**
     * 本进程当前存在的任务标识集合 —— 供 {@code JobScheduler.sync()} 做**反向对账**。
     *
     * <p>任务改名 / 改组后，Quartz 里的 JobKey 与数据库行的关联就断了：正向遍历数据永远发现不了它，
     * 旧触发器会按老 cron、老 `invokeTarget` 继续幽灵执行。所以要能枚举本进程现有的 JobKey 做差集清理。
     */
    public Set<JobKey> listJobKeys() throws SchedulerException {
        return scheduler.getJobKeys(GroupMatcher.anyJobGroup());
    }

    /** 取本进程当前任务用的执行类全名（没有该 JobDetail 时返回 null）；用于判断 `concurrent` 变化后需要重建 */
    public String getJobClassName(String jobName, String jobGroup) throws SchedulerException {
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(jobName, jobGroup));
        return jobDetail == null ? null : jobDetail.getJobClass().getName();
    }

    /**
     * 取本进程当前生效的 cron（没有该触发器时返回 null）。
     * <p>供调度对账使用：只有 cron **真的变了**才 reschedule，
     * 避免每次对账都重建触发器 —— 重建会重算下次触发时间，
     * 在触发点附近可能把这次执行按 misfire 策略丢掉。
     */
    public String getCronExpression(String jobName, String jobGroup) throws SchedulerException {
        Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(jobName, jobGroup));
        if (trigger instanceof CronTrigger cronTrigger) {
            return cronTrigger.getCronExpression();
        }
        return null;
    }
}
