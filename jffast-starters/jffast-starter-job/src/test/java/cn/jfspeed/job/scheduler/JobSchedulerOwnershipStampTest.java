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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.quartz.JobKey;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;
import cn.jfspeed.job.util.SpringContextHolder;

/**
 * 触发器归属：谁的 trigger 谁删、谁的任务谁认领。
 *
 * <p><b>问题背景</b>：Quartz 的 {@code SCHED_NAME}（{@code QRTZ_*.SCHED_NAME}）是行分区键。
 * 两个应用连同一个库**且**用同一个 {@code instanceName} 时会被并进同一个集群，
 * 彼此的 trigger 落在同一张表里。此时"这条 trigger 是谁的"不能靠命名空间推断 ——
 * 早先版本在正向对账里直接 {@code deleteJob}「不是我的行」，删掉的就是兄弟服务刚排好的
 * trigger（对方页面里任务还在、却不再执行）。
 *
 * <p><b>本轮的两处加固</b>：
 * <ol>
 *   <li>建 trigger 时把"建它的服务名"写进 JobDataMap（归属戳）；删除前读回来核对，
 *       戳写着别的服务就不删。没有戳的（升级前建的老 trigger）按自己的处理，行为与从前一致。</li>
 *   <li>{@code service_name} 为空的任务由**跑得动它的那个服务**认领（写库），
 *       避免两个都配了 {@code default-scope=true} 的服务同时排它 ⇒ 任务被执行两次。</li>
 * </ol>
 *
 * @author jffast
 */
class JobSchedulerOwnershipStampTest {

    private static final String GROUP = "DEFAULT";
    private static final String APP = "test-app";
    private static final String OTHER_APP = "other-app";

    private final JobProperties properties = new JobProperties();
    private final JobStore jobStore = mock(JobStore.class);
    private final QuartzManager quartzManager = mock(QuartzManager.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);

    @BeforeEach
    void setUp() {
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setDefaultScope(true);
        when(jobStore.isAvailable()).thenReturn(true);
        new SpringContextHolder().setApplicationContext(applicationContext);
    }

    @AfterEach
    void tearDown() {
        new SpringContextHolder().setApplicationContext(null);
    }

    private JobScheduler scheduler() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn(APP);
        return new JobScheduler(properties, jobStore, quartzManager,
                mock(JobAnnotationScanner.class), environment);
    }

    private static JobRecord row(String jobName, String serviceName, Integer status) {
        JobRecord record = new JobRecord();
        record.setId(1L);
        record.setJobName(jobName);
        record.setJobGroup(GROUP);
        record.setCronExpression("0 0/5 * * * ?");
        record.setInvokeTarget("demoJobService.run");
        record.setMisfirePolicy("3");
        record.setConcurrent(0);
        record.setStatus(status);
        record.setServiceName(serviceName);
        return record;
    }

    /** 让 QuartzManager 报告"这个 trigger 的归属戳是 owner"。 */
    private void stubOwner(String jobName, String owner) throws Exception {
        when(quartzManager.getJobDataValue(jobName, GROUP, JobScheduler.OWNER_KEY)).thenReturn(owner);
    }

    @Test
    @DisplayName("正向对账：别人的行 + 别人的归属戳 ⇒ 不得删它的 trigger")
    void foreignOwnedTriggerIsNotDeleted() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("other-job", OTHER_APP, 1)));
        when(quartzManager.checkExists("other-job", GROUP)).thenReturn(true);
        stubOwner("other-job", OTHER_APP);

        scheduler().sync();

        verify(quartzManager, never()).deleteJob(anyString(), anyString());
    }

    @Test
    @DisplayName("正向对账：没有归属戳的老 trigger ⇒ 仍按自己的处理（行为与从前一致）")
    void legacyTriggerWithoutStampIsStillDeleted() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("other-job", OTHER_APP, 1)));
        when(quartzManager.checkExists("other-job", GROUP)).thenReturn(true);
        // 归属戳读不到（升级前建的老 trigger）
        when(quartzManager.getJobDataValue(eq("other-job"), eq(GROUP), eq(JobScheduler.OWNER_KEY)))
                .thenReturn(null);

        scheduler().sync();

        verify(quartzManager).deleteJob("other-job", GROUP);
    }

    @Test
    @DisplayName("正向对账：自己暂停的任务 ⇒ 照旧删掉它的 trigger")
    void ownPausedTaskTriggerIsDeleted() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("my-job", APP, 0)));
        when(quartzManager.checkExists("my-job", GROUP)).thenReturn(true);
        stubOwner("my-job", APP);

        scheduler().sync();

        verify(quartzManager).deleteJob("my-job", GROUP);
    }

    @Test
    @DisplayName("孤儿清理：表里已无此行、但归属戳写着别的服务 ⇒ 也不替它清理")
    void orphanCleanupSkipsForeignOwnedTrigger() throws Exception {
        JobKey foreign = JobKey.jobKey("foreign-ghost", GROUP);
        when(quartzManager.listJobKeys()).thenReturn(Set.of(foreign));
        stubOwner("foreign-ghost", OTHER_APP);

        JobScheduler scheduler = scheduler();
        Object result = ReflectionTestUtils.invokeMethod(scheduler, "orphanCleanup", Set.of(), List.of());

        assertEquals(0, ((Number) result).intValue(), "别人的 trigger 被当成孤儿清理了");
        verify(quartzManager, never()).deleteJob(anyString(), anyString());
    }

    @Test
    @DisplayName("建 trigger 时把归属戳写进 JobDataMap")
    void ownerStampIsWrittenIntoJobDataMap() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("my-job", APP, 1)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);

        scheduler().sync();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(quartzManager).createJob(eq("my-job"), eq(GROUP), anyString(), any(),
                captor.capture(), anyString());
        assertEquals(APP, captor.getValue().get(JobScheduler.OWNER_KEY),
                "JobDataMap 必须带上归属戳，否则删除时无从判断这条 trigger 是谁的");
    }

    @Test
    @DisplayName("默认调度者认领：跑得动的无归属任务 ⇒ 写库认领到自己名下")
    void defaultSchedulerClaimsRunnableUnownedTask() {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null, 1)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        // 认领成功（认领是有竞争的，失败时行为见 JobSchedulerClaimOutcomeTest）
        when(jobStore.claim(any(), anyString())).thenReturn(true);

        scheduler().sync();

        verify(jobStore).claim(1L, APP);
    }

    @Test
    @DisplayName("跑不动的任务不认领：Bean 不在本进程 ⇒ 留给跑得动的那个服务")
    void doesNotClaimTaskItCannotRun() {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null, 1)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(false);

        scheduler().sync();

        verify(jobStore, never()).claim(any(), anyString());
    }

    @Test
    @DisplayName("已有归属的任务不认领")
    void doesNotClaimAlreadyOwnedTask() {
        when(jobStore.listAll()).thenReturn(List.of(row("owned", OTHER_APP, 1)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);

        scheduler().sync();

        verify(jobStore, never()).claim(any(), anyString());
    }

    @Test
    @DisplayName("非默认调度者不认领（default-scope=false 的服务不抢无归属任务）")
    void nonDefaultSchedulerDoesNotClaim() {
        properties.getScheduler().setDefaultScope(false);
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null, 1)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);

        scheduler().sync();

        verify(jobStore, never()).claim(any(), anyString());
        assertTrue(true);
    }
}
