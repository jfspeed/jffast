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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.common.core.context.AuditContext;
import cn.jfspeed.file.config.FileStorageProperties;
import cn.jfspeed.mybatis.config.DataScopeContext;

/**
 * 异步任务线程池：队列容量必须大于 0（N-06）；CallerRunsPolicy 在调用线程执行时必须
 * 保存并还原调用方上下文，不得污染请求线程。
 *
 * @author jffast
 */
class SysTaskRunnerQueueCapacityTest {

    @Test
    @DisplayName("队列容量 <= 0 启动即失败")
    void nonPositiveCapacityFailsFast() {
        FileStorageProperties properties = mock(FileStorageProperties.class);
        assertThrows(IllegalStateException.class, () -> new SysTaskRunner(properties, 0));
        assertThrows(IllegalStateException.class, () -> new SysTaskRunner(properties, -1));
    }

    @Test
    @DisplayName("队列满时调用线程执行任务，执行后恢复调用方数据权限与审计用户")
    void callerRunsRestoresCallerContext() throws Exception {
        SysTaskRunner runner = new SysTaskRunner(mock(FileStorageProperties.class), 1);
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        AtomicReference<String> taskScope = new AtomicReference<>();
        AtomicReference<Long> taskOperator = new AtomicReference<>();
        try {
            for (int i = 0; i < 2; i++) {
                runner.submit(null, () -> {
                    workersStarted.countDown();
                    try {
                        releaseWorkers.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(workersStarted.await(5, TimeUnit.SECONDS), "工作线程未启动");

            // 队列容量 1：先占满队列，再提交的任务必然由调用线程执行
            runner.submit(null, () -> { });
            DataScopeContext.set("caller-scope");
            AuditContext.setUserId(99L);

            runner.submit(new SysTaskContext(7L, "snapshot-scope"), () -> {
                taskScope.set(DataScopeContext.get());
                taskOperator.set(AuditContext.getUserId());
            });

            assertEquals("snapshot-scope", taskScope.get());
            assertEquals(7L, taskOperator.get());
            assertEquals("caller-scope", DataScopeContext.get());
            assertEquals(99L, AuditContext.getUserId());
        } finally {
            releaseWorkers.countDown();
            DataScopeContext.clear();
            AuditContext.clear();
            runner.shutdown();
        }
    }

    @Test
    @DisplayName("AuditContext 设置/读取/清除")
    void auditContextLifecycle() {
        try {
            AuditContext.setUserId(5L);
            assertEquals(5L, AuditContext.getUserId());
            AuditContext.setUserId(null);
            assertNull(AuditContext.getUserId());
        } finally {
            AuditContext.clear();
        }
    }
}
