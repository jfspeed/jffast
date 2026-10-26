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
package cn.jfspeed.job.scheduler;

import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.handler.DefaultQuartzJob;
import cn.jfspeed.job.handler.DisallowConcurrentQuartzJob;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定时任务对账的回归测试：改了**调用目标 / 失火策略**也必须重建触发器。
 *
 * <p>历史缺陷：{@code JobScheduler.sync()} 的重建条件只比较 cron、执行类、首轮三个信号，
 * 而 {@code invokeTarget}（真正被调用的 bean.method）与 {@code misfirePolicy}
 * 分别只存在于 JobDataMap 与触发器里。于是"在任务页改了执行目标"若没同时改 cron，
 * 触发器不会被重建 → 任务仍按**旧目标**执行，且「立即执行」也跑旧目标，
 * 直到服务重启（首轮强制重建）才恢复。现象是"改了没反应"。
 *
 * @author jffast
 */
class JobSchedulerSyncRebuildTest {

    private static final String JOB_NAME = "demo-job";
    private static final String JOB_GROUP = "DEFAULT";
    private static final String CRON = "0 0/5 * * * ?";

    private JobProperties properties;
    private JobStore jobStore;
    private QuartzManager quartzManager;
    private JobRecord row;

    @BeforeEach
    void setUp() throws Exception {
        properties = new JobProperties();
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setDefaultScope(true);

        jobStore = mock(JobStore.class);
        when(jobStore.isAvailable()).thenReturn(true);

        quartzManager = mock(QuartzManager.class);
        when(quartzManager.getCronExpression(JOB_NAME, JOB_GROUP)).thenReturn(CRON);
        // concurrent=0 ⇒ jobClassOf() 返回 DisallowConcurrentQuartzJob（与 JobScheduler 的判定一致）
        when(quartzManager.getJobClassName(JOB_NAME, JOB_GROUP))
                .thenReturn(DisallowConcurrentQuartzJob.class.getName());
        when(quartzManager.listJobKeys()).thenReturn(Set.of());
        when(quartzManager.checkExists(anyString(), anyString())).thenReturn(false);

        row = new JobRecord();
        row.setId(1L);
        row.setJobName(JOB_NAME);
        row.setJobGroup(JOB_GROUP);
        row.setInvokeTarget("demoJobService.cleanExpiredData");
        row.setCronExpression(CRON);
        row.setMisfirePolicy("3");
        row.setConcurrent(0);
        row.setStatus(1);
        row.setServiceName(null);
        when(jobStore.listAll()).thenReturn(List.of(row));
    }

    private JobScheduler newScheduler() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty(eq("spring.application.name"), anyString())).thenReturn("test-app");
        return new JobScheduler(properties, jobStore, quartzManager, mock(JobAnnotationScanner.class), environment);
    }

    @Test
    @DisplayName("改了调用目标（cron 不变）必须重建触发器")
    void invokeTargetChangeTriggersRebuild() throws Exception {
        when(quartzManager.getJobDataValue(JOB_NAME, JOB_GROUP, "invokeTarget"))
                .thenReturn("demoJobService.oldTarget");
        when(quartzManager.getMisfirePolicy(JOB_NAME, JOB_GROUP)).thenReturn("3");

        JobScheduler scheduler = newScheduler();
        // 第一轮：首轮强制重建（与本缺陷无关），先跑掉，让 firstSyncDone 置位
        scheduler.sync();
        org.mockito.Mockito.clearInvocations(quartzManager);

        scheduler.sync();

        verify(quartzManager).createJob(eq(JOB_NAME), eq(JOB_GROUP), eq(CRON), any(),
                any(), eq("3"));
    }

    @Test
    @DisplayName("改了失火策略（cron 不变）必须重建触发器")
    void misfirePolicyChangeTriggersRebuild() throws Exception {
        when(quartzManager.getJobDataValue(JOB_NAME, JOB_GROUP, "invokeTarget"))
                .thenReturn(row.getInvokeTarget());
        when(quartzManager.getMisfirePolicy(JOB_NAME, JOB_GROUP)).thenReturn("1");

        JobScheduler scheduler = newScheduler();
        scheduler.sync();
        org.mockito.Mockito.clearInvocations(quartzManager);

        scheduler.sync();

        verify(quartzManager).createJob(eq(JOB_NAME), eq(JOB_GROUP), eq(CRON), any(),
                any(), eq("3"));
    }

    @Test
    @DisplayName("什么都没改时不重建触发器（避免每次对账都重算下次触发时间）")
    void unchangedRowDoesNotRebuild() throws Exception {
        when(quartzManager.getJobDataValue(JOB_NAME, JOB_GROUP, "invokeTarget"))
                .thenReturn(row.getInvokeTarget());
        when(quartzManager.getMisfirePolicy(JOB_NAME, JOB_GROUP)).thenReturn("3");

        JobScheduler scheduler = newScheduler();
        scheduler.sync();
        org.mockito.Mockito.clearInvocations(quartzManager);

        scheduler.sync();

        verify(quartzManager, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("失火策略：空值与 \"3\" 视为同一策略，不触发无谓重建")
    void blankMisfirePolicyEqualsThree() throws Exception {
        row.setMisfirePolicy(null);
        when(quartzManager.getJobDataValue(JOB_NAME, JOB_GROUP, "invokeTarget"))
                .thenReturn(row.getInvokeTarget());
        when(quartzManager.getMisfirePolicy(JOB_NAME, JOB_GROUP)).thenReturn("3");

        JobScheduler scheduler = newScheduler();
        scheduler.sync();
        org.mockito.Mockito.clearInvocations(quartzManager);

        scheduler.sync();

        verify(quartzManager, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("未启用调度时不碰 Quartz")
    void disabledSchedulerDoesNothing() throws Exception {
        properties.getScheduler().setEnabled(false);
        JobScheduler scheduler = newScheduler();
        scheduler.sync();
        verify(quartzManager, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
        verify(quartzManager, never()).listJobKeys();
        verify(jobStore, never()).listAll();
        assert Collections.emptyList().isEmpty();
    }
}
