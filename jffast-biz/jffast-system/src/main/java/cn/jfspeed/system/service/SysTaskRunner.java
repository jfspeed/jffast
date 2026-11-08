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
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.file.config.FileStorageProperties;
import cn.jfspeed.mybatis.config.DataScopeContext;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 异步任务执行器：固定小线程池执行导入导出任务，队列满时由调用线程兜底执行。
 * <p>
 * 任务线程没有登录态：提交时通过 {@link SysTaskContext} 捕获的操作者与数据权限 SQL
 * 会在线程内恢复，执行完再还原调用线程原有上下文（{@link ThreadPoolExecutor.CallerRunsPolicy}
 * 会在调用线程直接执行任务，不还原会污染请求线程）。
 *
 * <p>租户上下文同理：提交侧捕获、执行侧恢复（见 {@link TenantScopeBridge}）。否则线程池里的
 * SQL 走的是「无租户上下文 = 全量视角」，A 租户的导出会把所有租户的数据都读到文件里。
 *
 * @author jffast
 */
@Component
public class SysTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(SysTaskRunner.class);

    private final ThreadPoolExecutor executor;

    /**
     * 租户上下文桥：Base 单租户没有实现（{@code getIfAvailable()} 为 null，行为不变）。
     * <p>用字段注入而非构造器注入，是为了不改变现有构造器签名（单测直接 new 本类）。
     */
    @Autowired
    private ObjectProvider<TenantScopeBridge> tenantScopeBridgeProvider;

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
        // 提交线程（请求线程）上的租户快照：必须在 execute 之前取，工作线程里拿不到登录态
        Long submitTenantId = currentTenantId();
        if (submitTenantId == null && bridge() != null) {
            // 多租户环境下的显式声明：不是「忘了传」，而是提交方本身就没有租户（如平台超管）
            log.info("【异步任务】提交线程无租户上下文，本次任务按平台级全量视角执行（显式声明）");
        }
        executor.execute(() -> {
            Long previousTenantId = currentTenantId();
            String previousDataScope = DataScopeContext.get();
            boolean previousResolved = DataScopeContext.isResolved();
            Long previousOperator = AuditContext.getUserId();
            attachTenant(submitTenantId);
            try {
                if (context != null) {
                    if (context.dataScopeSql() != null) {
                        DataScopeContext.set(context.dataScopeSql());
                    } else {
                        DataScopeContext.clear();
                    }
                    // 提交线程已判定过数据权限：工作线程不再拿空用户重算，否则「不受限」会被误判成拒绝
                    if (context.scopeResolved()) {
                        DataScopeContext.markResolved();
                    } else {
                        DataScopeContext.clearResolved();
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
                if (previousResolved) {
                    DataScopeContext.markResolved();
                } else {
                    DataScopeContext.clearResolved();
                }
                AuditContext.setUserId(previousOperator);
                attachTenant(previousTenantId);
            }
        });
    }

    /** 租户上下文桥；Base 单租户没有实现，返回 null（所有租户相关动作退化为空操作） */
    private TenantScopeBridge bridge() {
        return tenantScopeBridgeProvider == null ? null : tenantScopeBridgeProvider.getIfAvailable();
    }

    /** 当前线程的租户；无桥实现时恒为 null（单租户语义） */
    private Long currentTenantId() {
        TenantScopeBridge bridge = bridge();
        return bridge == null ? null : bridge.currentTenantId();
    }

    /**
     * 切换当前线程租户；{@code null} = 显式平台级视角（清空残留，避免线程复用串租户）。
     * <p>无桥实现（Base 单租户）时为空操作。
     */
    private void attachTenant(Long tenantId) {
        TenantScopeBridge bridge = bridge();
        if (bridge == null) {
            return;
        }
        if (tenantId == null) {
            bridge.detach();
        } else {
            bridge.attach(tenantId);
        }
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
