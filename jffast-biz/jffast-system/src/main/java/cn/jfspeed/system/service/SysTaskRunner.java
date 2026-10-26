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

    /**
     * 任务产物落盘目录（构造期固化并校验，见 {@link #taskDir()}）。
     */
    private final Path taskDir;

    public SysTaskRunner(FileStorageProperties fileStorageProperties,
                         @Value("${jffast.system.task.queue-capacity:100}") int queueCapacity,
                         @Value("${jffast.system.task.file-dir:task-files}") String taskFileDir) {
        if (queueCapacity <= 0) {
            throw new IllegalStateException("jffast.system.task.queue-capacity 必须大于 0，当前值：" + queueCapacity);
        }
        this.taskDir = resolveTaskDir(fileStorageProperties, taskFileDir);
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
     * 任务文件落盘目录，不存在则创建。目录在构造期解析一次（见 {@link #resolveTaskDir}），
     * 这里只负责补建缺失的父目录。
     */
    public Path taskDir() throws IOException {
        Files.createDirectories(taskDir);
        return taskDir;
    }

    /**
     * 解析并校验任务产物目录：必须落在本地存储根目录**之外**。
     *
     * <p>存储根（{@code jffast.file.storage.local.path}）被 {@code WebMvcConfig} 整体映射成
     * {@code /upload/**}，而 {@code SaTokenConfig} 把 {@code /upload/**} 列为免登录白名单——
     * 于是根目录里的每个文件都等于对外公开可下载。任务产物是异步导出的用户数据（手机号、邮箱）
     * 和管理员上传的待导入文件，一旦落在根内，持有 taskId 者无需登录即可取走整份表格，
     * 归属校验（{@code /sys/task/download}）被完全绕过。
     *
     * <p>因此这里选择启动即失败，而不是运行时静默泄露：配错的人当场收到可执行的提示，
     * 而不是等到数据被别人下走。
     */
    private static Path resolveTaskDir(FileStorageProperties fileStorageProperties, String taskFileDir) {
        Path dir = Paths.get(taskFileDir).toAbsolutePath().normalize();
        Path storageRoot = Paths.get(fileStorageProperties.getLocal().getPath())
                .toAbsolutePath().normalize();
        if (dir.startsWith(storageRoot)) {
            throw new IllegalStateException("jffast.system.task.file-dir 不能位于文件存储根 " + storageRoot
                    + " 之内（当前值：" + dir + "）。存储根经 /upload/** 免登录静态对外提供，"
                    + "任务产物落在里面等于公开下载；请改到根目录之外的独立目录，并在反向代理里禁止该目录对外访问。");
        }
        return dir;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
