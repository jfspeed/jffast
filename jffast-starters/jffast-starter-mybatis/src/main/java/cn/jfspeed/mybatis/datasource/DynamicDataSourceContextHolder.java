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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 动态数据源名称上下文（ThreadLocal 栈，支持嵌套调用切换后还原）
 *
 * @author jffast
 */
public final class DynamicDataSourceContextHolder {

    private static final ThreadLocal<Deque<String>> CONTEXT = ThreadLocal.withInitial(ArrayDeque::new);

    private DynamicDataSourceContextHolder() {
    }

    public static void push(String name) {
        CONTEXT.get().push(name == null ? "" : name);
    }

    /**
     * 弹出当前层并返回其下仍生效的数据源名（栈空返回 null，路由时按 primary 处理）
     */
    public static String pop() {
        Deque<String> stack = CONTEXT.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        return stack.peek();
    }

    /**
     * 当前生效的数据源名，空串/null 表示走 primary
     */
    public static String peek() {
        return CONTEXT.get().peek();
    }

    /**
     * 清理防线程复用残留
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
