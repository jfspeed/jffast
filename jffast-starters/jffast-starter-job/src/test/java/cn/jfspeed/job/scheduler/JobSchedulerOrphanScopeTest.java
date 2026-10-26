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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;

import org.mockito.ArgumentMatchers;
import org.quartz.JobKey;

/**
 * 孤儿触发器清理的范围。
 * <p>
 * {@code listJobKeys()} 用的是 {@code GroupMatcher.anyJobGroup()}，返回的是<b>整个 jobstore</b> 的键，
 * 不是"本服务创建的键"。共享同一套 QRTZ_ 表时（两个应用连同一个 MySQL 库，本地开发极易撞上了），
 * 早先版本拿本服务的 desired 做差集，于是别的服务刚排好的触发器会被当孤儿删掉——删除落在共享表上，
 * 对方页面里任务还在、却不再执行。
 * <p>
 * 现在只有"任务表里查无此行"的键才清（那正是改名/改组留下的幽灵执行，也是这条对账存在的理由）。
 *
 * @author jffast
 */
class JobSchedulerOrphanScopeTest {

    private static final String GROUP = "DEFAULT";

    private final JobProperties properties = new JobProperties();
    private final JobStore jobStore = mock(JobStore.class);
    private final QuartzManager quartzManager = mock(QuartzManager.class);

    private JobScheduler scheduler() {
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setDefaultScope(true);
        when(jobStore.isAvailable()).thenReturn(true);
        org.springframework.core.env.Environment environment =
                mock(org.springframework.core.env.Environment.class);
        when(environment.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn("test-app");
        return new JobScheduler(properties, jobStore, quartzManager, mock(JobAnnotationScanner.class), environment);
    }

    private static JobRecord row(String jobName, String serviceName) {
        JobRecord record = new JobRecord();
        record.setId(1L);
        record.setJobName(jobName);
        record.setJobGroup(GROUP);
        record.setCronExpression("0 0/5 * * * ?");
        record.setInvokeTarget("demoJobService.run");
        record.setConcurrent(0);
        record.setStatus(1);
        record.setServiceName(serviceName);
        return record;
    }

    @SuppressWarnings("unchecked")
    private int clean(JobScheduler scheduler, Set<JobKey> desired, List<JobRecord> rows) {
        Object result = ReflectionTestUtils.invokeMethod(scheduler, "orphanCleanup", desired, rows);
        return ((Number) result).intValue();
    }

    @Test
    @DisplayName("任务表仍有对应行的键不得删：那是别的服务的任务，不是孤儿")
    void foreignJobIsNotOrphan() throws Exception {
        JobKey foreign = JobKey.jobKey("other-app-job", GROUP);
        when(quartzManager.listJobKeys()).thenReturn(Set.of(foreign));

        int cleaned = clean(scheduler(), Set.of(), List.of(row("other-app-job", "other-app")));

        assertEquals(0, cleaned, "别的服务的任务被当成孤儿删除了");
        verify(quartzManager, never()).deleteJob(anyString(), anyString());
    }

    @Test
    @DisplayName("改名遗留的幽灵触发器必须清掉（任务表里已经没有这一行）")
    void renamedGhostIsRemoved() throws Exception {
        JobKey ghost = JobKey.jobKey("old-name", GROUP);
        when(quartzManager.listJobKeys()).thenReturn(Set.of(ghost));

        int cleaned = clean(scheduler(), Set.of(), List.of(row("new-name", "test-app")));

        assertEquals(1, cleaned, "改名后的旧 JobKey 必须被反向对账清掉，否则它按老 cron 继续幽灵执行");
        verify(quartzManager).deleteJob("old-name", GROUP);
    }

    @Test
    @DisplayName("本服务正在跑的任务不受影响")
    void ownDesiredKeyIsKept() throws Exception {
        JobKey mine = JobKey.jobKey("my-job", GROUP);
        when(quartzManager.listJobKeys()).thenReturn(Set.of(mine));

        int cleaned = clean(scheduler(), Set.of(mine), List.of(row("my-job", "test-app")));

        assertEquals(0, cleaned);
        verify(quartzManager, never()).deleteJob(anyString(), anyString());
    }

    @Test
    @DisplayName("清理中途抛错不吞掉已清理计数之外的异常：整体失败按下次重试处理")
    void cleanupFailureDoesNotBreakSync() throws Exception {
        when(quartzManager.listJobKeys()).thenReturn(Set.of(JobKey.jobKey("ghost", GROUP)));
        org.mockito.Mockito.doThrow(new org.quartz.SchedulerException("jobstore down"))
                .when(quartzManager).deleteJob(anyString(), anyString());

        int cleaned = clean(scheduler(), Set.of(), List.of());

        assertEquals(0, cleaned, "删除失败不得计入清理数");
        verify(quartzManager).deleteJob(ArgumentMatchers.eq("ghost"), ArgumentMatchers.eq(GROUP));
    }
}
