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
package cn.jfspeed.mybatis.config;

/**
 * 数据权限上下文 - ThreadLocal方式传递数据权限SQL片段
 * <p>
 * 由 DataPermissionAspect 设置，由 DataScopeInnerInterceptor 读取。
 * 确保数据权限SQL在同一线程内的多个Mapper调用中自动生效。
 *
 * @author jffast
 */
public class DataScopeContext {

    public static final String REQUEST_ATTRIBUTE = "jffast.dataScopeFragment";

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private static final ThreadLocal<Boolean> RESOLVED = new ThreadLocal<>();

    public static void set(String sqlFragment) {
        CONTEXT.set(sqlFragment);
    }

    public static String get() {
        return CONTEXT.get();
    }

    /**
     * 标记「本线程的数据权限已由提交线程按注解元数据判定过」。
     *
     * <p>异步任务线程没有登录态，若切面拿空用户重算只能得到「拒绝」，会把提交线程已经
     * 判定为不受限的任务（快照片段为 {@code null}）误杀。该标记由恢复任务上下文的一方
     * （{@code SysTaskRunner}）负责设置，切面据此跳过重算。</p>
     */
    public static void markResolved() {
        RESOLVED.set(Boolean.TRUE);
    }

    public static boolean isResolved() {
        return Boolean.TRUE.equals(RESOLVED.get());
    }

    public static void clearResolved() {
        RESOLVED.remove();
    }

    public static void clear() {
        CONTEXT.remove();
        RESOLVED.remove();
    }
}
