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

    public static void set(String sqlFragment) {
        CONTEXT.set(sqlFragment);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
