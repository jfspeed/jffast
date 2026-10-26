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
package cn.jfspeed.web.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * XssHttpServletRequestWrapper 测试：参数/头部 XSS 清洗。
 *
 * @author jffast
 */
@DisplayName("XssHttpServletRequestWrapper XSS 请求包装测试")
class XssHttpServletRequestWrapperTest {

    private XssHttpServletRequestWrapper wrapper() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("name", "<script>alert('xss')</script>");
        request.setParameter("plain", "normal-value");
        request.setParameter("multi", "<b>bold</b>", "safe");
        request.addHeader("h", "<i>hdr</i>");
        return new XssHttpServletRequestWrapper(request);
    }

    @Test
    @DisplayName("getParameter 应清洗 XSS 代码")
    void shouldCleanParameter() {
        XssHttpServletRequestWrapper w = wrapper();
        assertEquals("&lt;script&gt;alert('xss')&lt;/script&gt;", w.getParameter("name"));
        assertEquals("normal-value", w.getParameter("plain"));
    }

    @Test
    @DisplayName("getParameterValues 应逐个清洗")
    void shouldCleanParameterValues() {
        XssHttpServletRequestWrapper w = wrapper();
        String[] values = w.getParameterValues("multi");
        assertNotNull(values);
        assertEquals(2, values.length);
        assertEquals("&lt;b&gt;bold&lt;/b&gt;", values[0]);
        assertEquals("safe", values[1]);
    }

    @Test
    @DisplayName("getParameterValues 未命中参数应返回 null")
    void shouldReturnNullForMissingValues() {
        assertNull(wrapper().getParameterValues("missing"));
    }

    @Test
    @DisplayName("getParameterMap 应清洗全部参数值")
    void shouldCleanParameterMap() {
        var map = wrapper().getParameterMap();
        assertEquals("normal-value", map.get("plain")[0]);
        assertEquals("&lt;script&gt;alert('xss')&lt;/script&gt;", map.get("name")[0]);
        assertEquals("&lt;b&gt;bold&lt;/b&gt;", map.get("multi")[0]);
    }

    @Test
    @DisplayName("getHeader 应清洗 XSS 代码")
    void shouldCleanHeader() {
        XssHttpServletRequestWrapper w = wrapper();
        assertEquals("&lt;i&gt;hdr&lt;/i&gt;", w.getHeader("h"));
    }

    private MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private String readBody(XssHttpServletRequestWrapper w) throws IOException {
        return new String(w.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("JSON 请求体字符串值应被清洗")
    void shouldCleanJsonBody() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest("{\"name\":\"<script>alert('xss')</script>\",\"age\":18}"));
        String body = readBody(w);
        assertTrue(body.contains("&lt;script&gt;alert('xss')&lt;/script&gt;"));
        assertFalse(body.contains("<script>"));
        assertTrue(body.contains("\"age\":18"));
    }

    @Test
    @DisplayName("JSON 里 \\uXXXX 转义绕过写法同样被清洗")
    void shouldCleanUnicodeEscapedJson() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest("{\"name\":\"\\u003cscript\\u003e\"}"));
        assertFalse(readBody(w).contains("<script>"));
    }

    @Test
    @DisplayName("豁免字段应跳过清洗（保护 BPMN XML / 模板内容）")
    void shouldSkipConfiguredFields() throws IOException {
        String body = "{\"bpmnXml\":\"<definitions></definitions>\",\"name\":\"<b>x</b>\"}";
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest(body), true, Set.of("bpmnXml"));
        String cleaned = readBody(w);
        assertTrue(cleaned.contains("<definitions></definitions>"));
        assertTrue(cleaned.contains("&lt;b&gt;x&lt;/b&gt;"));
    }

    @Test
    @DisplayName("嵌套数组/对象中的字符串值应被清洗")
    void shouldCleanNestedJson() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest("{\"list\":[{\"tag\":\"<img>\"}]}"));
        assertTrue(readBody(w).contains("&lt;img&gt;"));
    }

    @Test
    @DisplayName("非 JSON 请求体应原样放行")
    void shouldPassThroughNonJsonBody() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("text/plain");
        request.setContent("<html></html>".getBytes(StandardCharsets.UTF_8));
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(request);
        assertEquals("<html></html>", readBody(w));
    }

    @Test
    @DisplayName("非法 JSON 原样放行，交由消息转换器报 400")
    void shouldPassThroughMalformedJson() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(jsonRequest("{bad json <x>}"));
        assertEquals("{bad json <x>}", readBody(w));
    }

    @Test
    @DisplayName("关闭 JSON 清洗开关时 body 不动")
    void shouldNotCleanJsonWhenDisabled() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest("{\"name\":\"<b>x</b>\"}"), false, Set.of());
        assertEquals("{\"name\":\"<b>x</b>\"}", readBody(w));
    }

    @Test
    @DisplayName("getReader 应读到清洗后的 JSON")
    void shouldReadCleanedJsonViaReader() throws IOException {
        XssHttpServletRequestWrapper w = new XssHttpServletRequestWrapper(
                jsonRequest("{\"name\":\"<b>x</b>\"}"));
        try (var reader = w.getReader()) {
            assertTrue(reader.readLine().contains("&lt;b&gt;x&lt;/b&gt;"));
        }
    }
}
