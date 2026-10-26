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
package cn.jfspeed.mybatis.datasource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动态数据源上下文（ThreadLocal 栈）单元测试。
 *
 * @author jffast
 */
class DynamicDataSourceContextHolderTest {

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clear();
    }

    @Nested
    @DisplayName("基本入栈 / 出栈")
    class BasicStack {

        @Test
        @DisplayName("push 后 peek 返回当前数据源名")
        void shouldPeekPushedName() {
            DynamicDataSourceContextHolder.push("slave");
            assertEquals("slave", DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("栈为空时 peek 返回 null（路由时按 primary 处理）")
        void shouldReturnNullWhenEmpty() {
            assertNull(DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("push null 归一化为空串，不产生 NPE")
        void shouldNormalizeNullToEmptyString() {
            DynamicDataSourceContextHolder.push(null);
            assertEquals("", DynamicDataSourceContextHolder.peek());
        }
    }

    @Nested
    @DisplayName("嵌套切换还原")
    class NestedSwitch {

        @Test
        @DisplayName("嵌套 push 后 pop 应还原到外层数据源")
        void shouldRestoreOuterAfterPop() {
            DynamicDataSourceContextHolder.push("outer");
            DynamicDataSourceContextHolder.push("inner");
            assertEquals("inner", DynamicDataSourceContextHolder.peek());

            assertEquals("outer", DynamicDataSourceContextHolder.pop());
            assertEquals("outer", DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("全部出栈后 peek 为 null")
        void shouldBeNullAfterAllPopped() {
            DynamicDataSourceContextHolder.push("only");
            assertNull(DynamicDataSourceContextHolder.pop());
            assertNull(DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("空栈 pop 不抛异常且返回 null")
        void shouldToleratePopOnEmptyStack() {
            assertNull(DynamicDataSourceContextHolder.pop());
        }
    }

    @Nested
    @DisplayName("线程隔离与清理")
    class IsolationAndClear {

        @Test
        @DisplayName("clear 后上下文重置，避免线程池复用残留")
        void shouldResetAfterClear() {
            DynamicDataSourceContextHolder.push("slave");
            DynamicDataSourceContextHolder.clear();
            assertNull(DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("不同线程的数据源上下文互不影响")
        void shouldIsolateAcrossThreads() throws InterruptedException {
            DynamicDataSourceContextHolder.push("main-thread");
            AtomicReference<String> workerPeek = new AtomicReference<>("unset");
            CountDownLatch done = new CountDownLatch(1);

            Thread worker = new Thread(() -> {
                workerPeek.set(DynamicDataSourceContextHolder.peek());
                DynamicDataSourceContextHolder.push("worker-thread");
                done.countDown();
            });
            worker.start();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            worker.join(5000);

            assertNull(workerPeek.get());
            assertEquals("main-thread", DynamicDataSourceContextHolder.peek());
        }
    }
}
