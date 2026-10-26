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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 数据权限 SQL 片段 ThreadLocal 上下文单元测试。
 *
 * @author jffast
 */
class DataScopeContextTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    @DisplayName("set 后 get 取回同一片段，clear 后归 null")
    void setGetClear() {
        assertNull(DataScopeContext.get());

        DataScopeContext.set("dept_id IN (1,2)");
        assertEquals("dept_id IN (1,2)", DataScopeContext.get());

        DataScopeContext.clear();
        assertNull(DataScopeContext.get());
    }

    @Test
    @DisplayName("set(null) 可显式清空（与 clear 等价）")
    void setNullClears() {
        DataScopeContext.set("x");
        DataScopeContext.set(null);
        assertNull(DataScopeContext.get());
    }
}
