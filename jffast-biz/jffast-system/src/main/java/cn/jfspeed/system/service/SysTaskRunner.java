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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import cn.jfspeed.common.core.context.AuditContext;
import cn.jfspeed.file.config.FileStorageProperties;
import cn.jfspeed.mybatis.config.DataScopeContext;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行器：固定小线程池执行导入导出任务，队列满时由调用线程兜底执行。
 * <p>
 * 任务线程没有登录态：提交时通过 {@link SysTaskContext} 捕获的操作者与数据权限 SQL
 * 会在线程内恢复，执行完再还原调用线程原有上下文（{@link ThreadPoolExecutor.CallerRunsPolicy}
 * 会在调用线程直接执行任务，不还原会污染请求线程）。
 *
 * @author jffast
 */
@Component
public class SysTaskRunner {

    private final ThreadPoolExecutor executor;

    private final FileStorageProperties fileStorageProperties;

    public SysTaskRunner(FileStorageProperties fileStorageProperties,
                         @Value("${jffast.system.task.queue-capacity:100}") int queueCapacity) {
        if (queueCapacity <= 0) {
            throw new IllegalStateException("jffast.system.task.queue-capacity 必须大于 0，当前值：" + queueCapacity);
        }
        this.fileStorageProperties = fileStorageProperties;
        AtomicInteger threadIndex = new AtomicInteger(1);
        this.executor = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("sys-task-" + threadIndex.getAndIncrement());
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 提交任务（不带任务上下文，供无数据权限/审计要求的场景使用）
     */
    public void submit(Runnable task) {
        submit(null, task);
    }

    /**
     * 提交任务并在线程内恢复提交方捕获的操作者与数据权限快照
     */
    public void submit(SysTaskContext context, Runnable task) {
        executor.execute(() -> {
            String previousDataScope = DataScopeContext.get();
            Long previousOperator = AuditContext.getUserId();
            try {
                if (context != null) {
                    if (context.dataScopeSql() != null) {
                        DataScopeContext.set(context.dataScopeSql());
                    } else {
                        DataScopeContext.clear();
                    }
                    AuditContext.setUserId(context.operatorId());
                }
                task.run();
            } finally {
                if (previousDataScope != null) {
                    DataScopeContext.set(previousDataScope);
                } else {
                    DataScopeContext.clear();
                }
                AuditContext.setUserId(previousOperator);
            }
        });
    }

    /**
     * 任务文件落盘目录（本地存储目录下 task 子目录），不存在则创建
     */
    public Path taskDir() throws IOException {
        Path dir = Paths.get(fileStorageProperties.getLocal().getPath(), "task");
        Files.createDirectories(dir);
        return dir;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
