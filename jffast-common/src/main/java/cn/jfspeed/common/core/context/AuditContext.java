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
 * 审计用户上下文：异步任务线程没有登录态，由任务包装器在进入任务前设置操作者，
 * {@code AutoFillHandler} 优先取这里的值作为 create_by / update_by。
 *
 * @author jffast
 */
public final class AuditContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AuditContext() {
    }

    public static void setUserId(Long userId) {
        if (userId == null) {
            USER_ID.remove();
        } else {
            USER_ID.set(userId);
        }
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
