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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;

import cn.jfspeed.job.handler.DefaultQuartzJob;

/**
 * 「立即执行」前的补建（{@code ensureJobExists}）在有键无详情时必须兜住。
 * <p>
 * 原实现是 {@code checkExists} 为真就直接回写字段并 return，而字段回写里读到
 * {@code getJobDetail == null} 时<b>静默返回</b>。JDBC jobstore 下"键在、详情已被并发删掉"
 * 是能出现的（同事务里的删除、集群节点的视图延迟）。后果是随后那次
 * {@code scheduler.triggerJob(jobKey)} 对不存在的 JobKey <b>不报错</b>，
 * 于是页面点了"执行"什么也没跑，日志里连一条失败都搜不到。
 *
 * @author jffast
 */
class QuartzManagerEnsureJobExistsTest {

    private static final String JOB_NAME = "demo-job";
    private static final String JOB_GROUP = "DEFAULT";
    private static final JobKey JOB_KEY = JobKey.jobKey(JOB_NAME, JOB_GROUP);

    private final Scheduler scheduler = mock(Scheduler.class);
    private final QuartzManager manager = new QuartzManager(scheduler);

    @Test
    @DisplayName("无此任务：正常新建")
    void createsWhenAbsent() throws Exception {
        when(scheduler.checkExists(JOB_KEY)).thenReturn(false);

        manager.ensureJobExists(JOB_NAME, JOB_GROUP, DefaultQuartzJob.class,
                Map.of("invokeTarget", "demoJobService.run"));

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
    }

    @Test
    @DisplayName("键在但详情读不到：必须补建，不能让触发请求打空")
    void rebuildsWhenKeyExistsButDetailMissing() throws Exception {
        when(scheduler.checkExists(JOB_KEY)).thenReturn(true);
        when(scheduler.getJobDetail(JOB_KEY)).thenReturn(null);

        manager.ensureJobExists(JOB_NAME, JOB_GROUP, DefaultQuartzJob.class,
                Map.of("invokeTarget", "demoJobService.run"));

        verify(scheduler).addJob(any(JobDetail.class), eq(true));
    }

    @Test
    @DisplayName("详情在且目标变了：写回新 JobDataMap")
    void refreshesChangedTarget() throws Exception {
        JobDetail existing = mock(JobDetail.class);
        when(existing.getJobDataMap()).thenReturn(new JobDataMap(Map.of("invokeTarget", "demoJobService.old")));
        when(scheduler.checkExists(JOB_KEY)).thenReturn(true);
        when(scheduler.getJobDetail(JOB_KEY)).thenReturn(existing);

        manager.ensureJobExists(JOB_NAME, JOB_GROUP, DefaultQuartzJob.class,
                Map.of("invokeTarget", "demoJobService.new"));

        verify(scheduler).addJob(existing, true);
        assertEquals("demoJobService.new", existing.getJobDataMap().get("invokeTarget"));
    }

    @Test
    @DisplayName("详情在且没变化：不重复写，免得每次点执行都重排一次 JobDetail")
    void skipsWriteWhenUnchanged() throws Exception {
        JobDetail existing = mock(JobDetail.class);
        when(existing.getJobDataMap()).thenReturn(new JobDataMap(Map.of("invokeTarget", "demoJobService.run")));
        when(scheduler.checkExists(JOB_KEY)).thenReturn(true);
        when(scheduler.getJobDetail(JOB_KEY)).thenReturn(existing);

        manager.ensureJobExists(JOB_NAME, JOB_GROUP, DefaultQuartzJob.class,
                Map.of("invokeTarget", "demoJobService.run"));

        verify(scheduler, never()).addJob(any(JobDetail.class), eq(true));
    }

    @Test
    @DisplayName("补建不得碰已挂上的触发器（重建会重算下次触发时间）")
    void rebuildDoesNotRescheduleTrigger() throws Exception {
        when(scheduler.checkExists(JOB_KEY)).thenReturn(true);
        when(scheduler.getJobDetail(JOB_KEY)).thenReturn(null);

        manager.ensureJobExists(JOB_NAME, JOB_GROUP, DefaultQuartzJob.class, Map.of());

        verify(scheduler, never()).scheduleJob(any(JobDetail.class), any(org.quartz.Trigger.class));
        verify(scheduler, never()).unscheduleJob(any(TriggerKey.class));
        verify(scheduler, never()).deleteJob(any(JobKey.class));
    }
}
