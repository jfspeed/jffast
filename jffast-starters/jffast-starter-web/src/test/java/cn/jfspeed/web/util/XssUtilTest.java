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
package cn.jfspeed.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
@DisplayName("XssUtil XSS 过滤工具测试")
class XssUtilTest {

    @Test
    @DisplayName("应转义脚本标签中的尖括号")
    void shouldEscapeAngleBrackets() {
        String input = "<script>alert('xss')</script>";
        assertEquals("&lt;script&gt;alert('xss')&lt;/script&gt;", XssUtil.clean(input));
    }

    @Test
    @DisplayName("应转义 & 符号")
    void shouldEscapeAmpersand() {
        assertEquals("a &amp; b", XssUtil.clean("a & b"));
    }

    @Test
    @DisplayName("不应破坏正常单双引号与括号")
    void shouldNotOverEscape() {
        String input = "It's a (test) with \"quotes\"";
        assertEquals(input, XssUtil.clean(input));
    }

    @Test
    @DisplayName("空值安全")
    void shouldHandleNullAndEmpty() {
        assertNull(XssUtil.clean(null));
        assertEquals("", XssUtil.clean(""));
    }
}
