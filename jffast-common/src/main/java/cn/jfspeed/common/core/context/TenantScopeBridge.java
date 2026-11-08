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
package cn.jfspeed.common.core.context;

/**
 * 租户上下文跨线程桥（Base 单租户无实现，Pro 的租户 Starter 提供实现）。
 *
 * <p>背景：租户上下文是 ThreadLocal，而异步任务（线程池 / Quartz）执行在别的线程上，
 * 不显式传递就会「掉上下文」—— 租户拦截器在无上下文时直接放行，等于**静默全量视角**。
 * 因此凡是从请求线程移交到后台线程的入口，都必须在提交侧捕获、在执行侧恢复。
 *
 * <p>约定：{@link #attach} 传入 {@code null} 表示**显式的平台级视角**，实现应清空当前线程
 * 可能残留的租户，而不是继承上一次任务的值（线程池的线程会被复用）。
 *
 * @author jffast
 */
public interface TenantScopeBridge {

    /** 当前线程的租户；{@code null} = 平台级（无租户）视角 */
    Long currentTenantId();

    /** 把当前线程切到指定租户；{@code null} 同 {@link #detach()} */
    void attach(Long tenantId);

    /** 清空当前线程的租户上下文 */
    void detach();
}
