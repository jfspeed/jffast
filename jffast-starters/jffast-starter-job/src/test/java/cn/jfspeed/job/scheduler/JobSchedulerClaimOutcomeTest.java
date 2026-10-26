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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;
import cn.jfspeed.job.util.SpringContextHolder;

/**
 * **认领结果决定排不排期**。
 *
 * <p><b>缺陷背景</b>：无归属任务（{@code service_name} 为空）的归属认领是**有竞争**的 ——
 * 库里靠 {@code UPDATE ... WHERE service_name IS NULL} 保证同一行只有一个赢家。
 * 早先版本在正向对账里先按"我是默认调度者"放行、**之后**才去认领，而且**丢掉了认领的返回值**：
 * 认领失败（这一行已被别的服务抢走）也照样排期。
 * 于是两个都配了 {@code default-scope=true} 的服务会把同一个任务**各排一次 ⇒ 执行两次**。
 *
 * <p><b>本文件的契约</b>：
 * <ol>
 *   <li>认领成功 ⇒ 本服务排它；</li>
 *   <li>认领失败（已被别的服务认领）⇒ 本服务**不排它**，并清掉本进程可能残留的触发器
 *       （否则上一轮排过的触发器还在跑，仍是重复执行）；</li>
 *   <li>认领抛异常（数据库抖动）⇒ 保守**不排期**（宁可漏一轮也不重复执行）；</li>
 *   <li>本服务**跑不动**（调用目标的 Bean 不在本进程）⇒ 照旧按默认调度者口径排期，
 *       不因"跑不动"就静默地让任务永远不执行。</li>
 * </ol>
 *
 * @author jffast
 */
class JobSchedulerClaimOutcomeTest {

    private static final String GROUP = "DEFAULT";
    private static final String APP = "app-a";
    private static final String OTHER_APP = "app-b";

    private final JobProperties properties = new JobProperties();
    private final JobStore jobStore = mock(JobStore.class);
    private final QuartzManager quartzManager = mock(QuartzManager.class);
    private final ApplicationContext applicationContext = mock(ApplicationContext.class);

    @BeforeEach
    void setUp() throws Exception {
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setDefaultScope(true);
        when(jobStore.isAvailable()).thenReturn(true);
        when(quartzManager.listJobKeys()).thenReturn(Set.of());
        new SpringContextHolder().setApplicationContext(applicationContext);
    }

    @AfterEach
    void tearDown() {
        new SpringContextHolder().setApplicationContext(null);
    }

    private JobScheduler scheduler(String appName) {
        Environment environment = mock(Environment.class);
        when(environment.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn(appName);
        return new JobScheduler(properties, jobStore, quartzManager,
                mock(JobAnnotationScanner.class), environment);
    }

    private static JobRecord row(String jobName, String serviceName) {
        JobRecord record = new JobRecord();
        record.setId(1L);
        record.setJobName(jobName);
        record.setJobGroup(GROUP);
        record.setCronExpression("0 0/5 * * * ?");
        record.setInvokeTarget("demoJobService.run");
        record.setMisfirePolicy("3");
        record.setConcurrent(0);
        record.setStatus(1);
        record.setServiceName(serviceName);
        return record;
    }

    private void stubOwner(String jobName, String owner) throws Exception {
        when(quartzManager.getJobDataValue(jobName, GROUP, JobScheduler.OWNER_KEY)).thenReturn(owner);
    }

    @Test
    @DisplayName("认领失败（已被别的服务认领）⇒ 本服务不排期")
    void claimLostMeansNotScheduled() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        when(jobStore.claim(any(), anyString())).thenReturn(false);

        scheduler(APP).sync();

        verify(quartzManager, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("认领失败 ⇒ 清掉本进程残留的触发器（上一轮排过的那个仍在跑）")
    void claimLostRemovesResidualOwnTrigger() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        when(jobStore.claim(any(), anyString())).thenReturn(false);
        when(quartzManager.checkExists("unowned", GROUP)).thenReturn(true);
        stubOwner("unowned", APP);

        scheduler(APP).sync();

        verify(quartzManager).deleteJob("unowned", GROUP);
    }

    @Test
    @DisplayName("认领失败 ⇒ 不删归属戳写着别的服务的触发器")
    void claimLostDoesNotRemoveForeignTrigger() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        when(jobStore.claim(any(), anyString())).thenReturn(false);
        when(quartzManager.checkExists("unowned", GROUP)).thenReturn(true);
        stubOwner("unowned", OTHER_APP);

        scheduler(APP).sync();

        verify(quartzManager, never()).deleteJob(anyString(), anyString());
    }

    @Test
    @DisplayName("认领成功 ⇒ 本服务排期")
    void claimWonMeansScheduled() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        when(jobStore.claim(any(), anyString())).thenReturn(true);

        scheduler(APP).sync();

        verify(quartzManager).createJob(eq("unowned"), eq(GROUP), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("认领抛异常（数据库抖动）⇒ 保守不排期，不重复执行")
    void claimExceptionMeansNotScheduled() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        when(jobStore.claim(any(), anyString())).thenThrow(new RuntimeException("db down"));

        scheduler(APP).sync();

        verify(quartzManager, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("跑不动的任务（Bean 不在本进程）⇒ 不认领，但照旧按默认调度者口径排期")
    void unrunnableTaskIsStillScheduled() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(false);

        scheduler(APP).sync();

        verify(jobStore, never()).claim(any(), anyString());
        verify(quartzManager).createJob(eq("unowned"), eq(GROUP), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("两个默认调度者抢同一条无归属任务 ⇒ 只有一个排期（认领赢家才排）")
    void onlyClaimWinnerSchedulesTheTask() throws Exception {
        when(jobStore.listAll()).thenReturn(List.of(row("unowned", null)));
        when(applicationContext.containsBean("demoJobService")).thenReturn(true);
        // 认领有竞争：app-a 抢到，app-b 没抢到
        when(jobStore.claim(1L, APP)).thenReturn(true);
        when(jobStore.claim(1L, OTHER_APP)).thenReturn(false);

        QuartzManager quartzA = mock(QuartzManager.class);
        QuartzManager quartzB = mock(QuartzManager.class);
        when(quartzA.listJobKeys()).thenReturn(Set.of());
        when(quartzB.listJobKeys()).thenReturn(Set.of());
        Environment envA = mock(Environment.class);
        when(envA.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn(APP);
        Environment envB = mock(Environment.class);
        when(envB.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn(OTHER_APP);

        new JobScheduler(properties, jobStore, quartzA, mock(JobAnnotationScanner.class), envA).sync();
        new JobScheduler(properties, jobStore, quartzB, mock(JobAnnotationScanner.class), envB).sync();

        verify(quartzA).createJob(eq("unowned"), eq(GROUP), anyString(), any(), any(), anyString());
        verify(quartzB, never()).createJob(anyString(), anyString(), anyString(), any(), any(), anyString());
    }
}
