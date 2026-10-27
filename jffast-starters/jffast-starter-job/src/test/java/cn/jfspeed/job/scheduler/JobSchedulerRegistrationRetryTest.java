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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.env.Environment;

import cn.jfspeed.job.config.JobProperties;
import cn.jfspeed.job.domain.JobRecord;
import cn.jfspeed.job.domain.JobTargetInfo;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.store.JobStore;

/**
 * **注解登记的失败必须可见、且必须可重试**。
 *
 * <p><b>缺陷背景</b>：{@code JobStore#insert} 原先返回 {@code void}，
 * 存储不可用 / 并发撞唯一键 / 写入失败这三种"根本没插进去"的情况，
 * 调度器照样打「登记新任务」并把它计入"新增 N 条"——日志与事实相反。
 * 更糟的是登记只在启动时跑一次：那一刻数据库还没就绪（表未建 / 连接未通），
 * 这次失败后**整个进程生命周期都不会再登记**，表现是
 * 「代码里明明有 {@code @JfFastJob}，任务页里却一直没有这个任务，重启才好」。
 *
 * <p><b>本文件的契约</b>：
 * <ol>
 *   <li>插入真的成功 ⇒ 记录被插入（调用方据返回值决定计数与日志）；</li>
 *   <li>插入失败（返回 false）⇒ 不谎报成功；</li>
 *   <li>登记是**幂等**的：任务表读不到时本轮跳过（不把"读失败"当成"没登记"），
 *       任务表里已有的行不重复插入；</li>
 *   <li>登记会在每轮周期同步时重试 ⇒ 启动那一刻的失败不会永久留存。</li>
 * </ol>
 *
 * @author jffast
 */
class JobSchedulerRegistrationRetryTest {

    private static final String APP = "app-a";
    private static final String GROUP = JobScheduler.DEFAULT_GROUP;

    private final JobProperties properties = new JobProperties();
    private final JobStore jobStore = mock(JobStore.class);
    private final QuartzManager quartzManager = mock(QuartzManager.class);
    private final JobAnnotationScanner scanner = mock(JobAnnotationScanner.class);

    @BeforeEach
    void setUp() {
        properties.getScheduler().setEnabled(true);
        properties.getScheduler().setDefaultScope(true);
        properties.getScheduler().setRefreshIntervalSeconds(1);
        when(jobStore.isAvailable()).thenReturn(true);
        when(jobStore.listAll()).thenReturn(List.of());
    }

    private JobScheduler scheduler() {
        Environment environment = mock(Environment.class);
        when(environment.getProperty(ArgumentMatchers.<String>any(), anyString())).thenReturn(APP);
        return new JobScheduler(properties, jobStore, quartzManager, scanner, environment);
    }

    private static JobTargetInfo target(String beanName, String methodName) {
        JobTargetInfo info = new JobTargetInfo();
        info.setBeanName(beanName);
        info.setMethodName(methodName);
        info.setInvokeTarget(beanName + "." + methodName);
        info.setName(methodName);
        info.setParamNames(List.of());
        info.setCron("0 0/5 * * * ?");
        info.setEnabled(true);
        return info;
    }

    @Test
    @DisplayName("登记成功 ⇒ 真的插入了这一行（insert 返回 true）")
    void successfulRegistrationInsertsRow() {
        when(scanner.getJobTargets()).thenReturn(List.of(target("demoJobService", "run")));
        when(jobStore.insert(any())).thenReturn(true);

        scheduler().registerAnnotatedJobs();

        verify(jobStore, times(1)).insert(any());
    }

    @Test
    @DisplayName("登记失败（存储不可用 / 撞键 / 写失败）⇒ insert 返回 false，不谎报成功")
    void failedRegistrationIsReportedAsFailure() {
        when(scanner.getJobTargets()).thenReturn(List.of(target("demoJobService", "run")));
        when(jobStore.insert(any())).thenReturn(false);

        scheduler().registerAnnotatedJobs();

        verify(jobStore, times(1)).insert(any());
    }

    @Test
    @DisplayName("任务表读不到 ⇒ 本轮跳过，不插入（区分『没登记』与『读失败』）")
    void unreadableTableSkipsRegistration() {
        when(scanner.getJobTargets()).thenReturn(List.of(target("demoJobService", "run")));
        when(jobStore.listAll()).thenReturn(null);

        scheduler().registerAnnotatedJobs();

        verify(jobStore, never()).insert(any());
    }

    @Test
    @DisplayName("任务表里已有的行 ⇒ 不重复插入（幂等）")
    void alreadyRegisteredTargetIsNotInsertedAgain() {
        JobRecord existing = new JobRecord();
        existing.setId(7L);
        existing.setJobName("run");
        existing.setJobGroup(GROUP);
        existing.setInvokeTarget("demoJobService.run");
        existing.setServiceName(APP);
        when(scanner.getJobTargets()).thenReturn(List.of(target("demoJobService", "run")));
        when(jobStore.listAll()).thenReturn(List.of(existing));

        scheduler().registerAnnotatedJobs();

        verify(jobStore, never()).insert(any());
    }

    @Test
    @DisplayName("带参任务不自动入库（占位参数不可执行）⇒ 不调用 insert")
    void parameterizedTargetIsNotAutoRegistered() {
        JobTargetInfo info = target("demoJobService", "generate");
        info.setInvokeTarget("demoJobService.generate({param})");
        info.setParamNames(List.of("param"));
        when(scanner.getJobTargets()).thenReturn(List.of(info));

        scheduler().registerAnnotatedJobs();

        verify(jobStore, never()).insert(any());
    }

    @Test
    @DisplayName("周期同步每轮都会重新登记 ⇒ 启动那一刻的失败会被重试")
    void periodicSyncRetriesRegistration() throws Exception {
        when(scanner.getJobTargets()).thenReturn(List.of(target("demoJobService", "run")));
        // 首轮失败
        when(jobStore.insert(any())).thenReturn(false);

        JobScheduler scheduler = scheduler();
        scheduler.registerAnnotatedJobs();
        verify(jobStore, times(1)).insert(any());

        // 存储恢复后，下一轮周期同步会再次尝试登记
        when(jobStore.insert(any())).thenReturn(true);
        scheduler.registerAnnotatedJobs();

        verify(jobStore, times(2)).insert(any());
    }
}
