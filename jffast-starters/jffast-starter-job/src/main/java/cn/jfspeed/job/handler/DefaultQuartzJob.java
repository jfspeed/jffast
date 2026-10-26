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
package cn.jfspeed.job.handler;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobLogRecord;
import cn.jfspeed.job.lock.JobLock;
import cn.jfspeed.job.store.JobStore;
import cn.jfspeed.job.util.JobInvokeUtil;
import cn.jfspeed.job.util.SpringContextHolder;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 任务执行体（Starter 内，**取代原 `jffast-system` 的 `QuartzJobExecution`**）。
 *
 * <p>与旧实现的差别：<b>执行前后包一层分布式锁</b>，并自带日志落库（经 {@link JobStore}），
 * 因此任何服务（不必依赖 `jffast-system`）都能跑自己的任务。
 *
 * <p>锁的语义：
 * <ul>
 *   <li>抢到锁 ⇒ 正常执行，执行完立即释放（因此下一个调度周期不受影响）；</li>
 *   <li>抢不到 ⇒ 说明**另一个副本正在执行同一个任务**，本次直接跳过，
 *       <b>不写 `sys_job_log`</b>（跳过不是失败，写进去只会让日志表翻倍）；</li>
 *   <li>TTL 只兜底"实例崩溃导致锁没释放"的情况 —— TTL 内该任务的后续触发会被跳过，
 *       任务耗时可能超过 TTL 时应调大 {@code jffast.job.lock.ttl-seconds}。</li>
 * </ul>
 *
 * @author jffast
 */
public class DefaultQuartzJob extends AbstractQuartzJob {

    private static final Logger log = LoggerFactory.getLogger(DefaultQuartzJob.class);

    /** 本次执行是否因"锁被其它实例持有"而跳过（跳过则不记成功日志） */
    private boolean skipped;

    @Override
    protected void doExecute(JobExecutionContext context) throws Exception {
        String invokeTarget = context.getJobDetail().getJobDataMap().getString("invokeTarget");
        if (StrUtil.isBlank(invokeTarget)) {
            throw new IllegalArgumentException("调用目标(invokeTarget)不能为空");
        }
        JobKey key = context.getJobDetail().getKey();

        JobLock lock = SpringContextHolder.tryGetBean(JobLock.class);
        String lockKey = null;
        if (lock != null) {
            // ⚠️ 锁 key 必须带**计划触发时刻**：多副本的内存 Quartz 各自触发同一 tick，
            //    只用 jobName 做 key 会漏 —— 先跑的副本执行完**立刻释放**，
            //    慢半拍的副本随后又能抢到同一个 key 再跑一遍
            //    （2026-09-23 双副本实测：16:10:00 出现 2 条执行记录，被 probe-jobs.ps1 抓到）。
            //    带上 scheduledFireTime 后，同一个 tick 在所有副本上是**同一个 key** ⇒ 只有一个能抢到。
            //    并且**不主动释放**（见下方 finally 注释），让它按 TTL 自然过期。
            java.util.Date fireTime = context.getScheduledFireTime();
            lockKey = "jffast:job:lock:" + key.getGroup() + ":" + key.getName()
                    + (fireTime == null ? "" : ":" + fireTime.getTime());
            JobProperties properties = SpringContextHolder.tryGetBean(JobProperties.class);
            long ttlMillis = (properties == null ? 300L : properties.getLock().getTtlSeconds()) * 1000L;
            if (lock.tryLock(lockKey, ttlMillis) == null) {
                skipped = true;
                log.info("【定时任务】{} 本次触发已由其它实例执行，本实例跳过（多副本防重复）", key.getName());
                return;
            }
        }

        try {
            JobInvokeUtil.invokeMethod(invokeTarget);
        } finally {
            // **刻意不释放**：释放会让"慢半拍"的副本抢到同一个 tick 的锁、把这次触发再跑一遍。
            // key 自带触发时刻 ⇒ 不会影响下一次触发（新 tick = 新 key），旧 key 由 TTL 回收。
            // 副作用：Redis 里会短暂保留若干 `jffast:job:lock:*`（数量 = 任务数 × TTL 内的触发次数），
            //         TTL 由 `jffast.job.lock.ttl-seconds` 控制（默认 300s）。
        }
    }

    @Override
    protected void after(JobExecutionContext context, LocalDateTime startTime, Exception exception) {
        if (skipped) {
            return;
        }
        JobStore store = SpringContextHolder.tryGetBean(JobStore.class);
        if (store == null || !store.isAvailable()) {
            log.debug("【定时任务】任务存储不可用，跳过执行日志落库");
            return;
        }
        JobKey key = context.getJobDetail().getKey();
        LocalDateTime endTime = LocalDateTime.now();
        JobLogRecord record = new JobLogRecord();
        record.setJobName(key.getName());
        record.setJobGroup(key.getGroup());
        record.setInvokeTarget(context.getJobDetail().getJobDataMap().getString("invokeTarget"));
        record.setStartTime(startTime);
        record.setEndTime(endTime);
        record.setDuration(Duration.between(startTime, endTime).toMillis());
        if (exception != null) {
            record.setStatus(2);
            record.setErrorMsg(StrUtil.sub(exception.getMessage(), 0, 2000));
        } else {
            record.setStatus(1);
        }
        store.insertLog(record);
    }
}
