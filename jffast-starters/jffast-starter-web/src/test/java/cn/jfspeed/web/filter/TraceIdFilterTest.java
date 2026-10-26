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

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TraceIdFilter 测试：traceId/spanId 生成与透传、MDC 写入清理、响应头输出。
 *
 * @author jffast
 */
@DisplayName("TraceIdFilter 链路追踪过滤器测试")
@SuppressWarnings("unchecked")
class TraceIdFilterTest {

    private final AtomicReference<String> mdcInChain = new AtomicReference<>();
    private final FilterChain recordingChain = (req, res) -> mdcInChain.set(MDC.get("traceId"));

    private ObjectProvider<Tracer> emptyProvider() {
        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @Test
    @DisplayName("无 Tracer 时应生成 traceId/spanId 并写入响应头与 MDC")
    void shouldGenerateIdsWhenNoTracer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(emptyProvider()).doFilter(request, response, recordingChain);

        String traceId = response.getHeader("X-Trace-Id");
        String spanId = response.getHeader("X-Span-Id");
        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
        assertNotNull(spanId);
        assertFalse(spanId.isBlank());
        assertEquals(traceId, mdcInChain.get());
        // 请求后 MDC 必须清理干净，避免线程复用串号
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
    }

    @Test
    @DisplayName("请求头带 traceId/spanId 时应原样透传")
    void shouldPropagateIncomingHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "req-trace");
        request.addHeader("X-Span-Id", "req-span");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(emptyProvider()).doFilter(request, response, recordingChain);

        assertEquals("req-trace", response.getHeader("X-Trace-Id"));
        assertEquals("req-span", response.getHeader("X-Span-Id"));
        assertEquals("req-trace", mdcInChain.get());
    }

    @Test
    @DisplayName("空串请求头视为未提供，应生成新 ID")
    void shouldTreatBlankHeaderAsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(emptyProvider()).doFilter(request, response, recordingChain);

        assertNotNull(response.getHeader("X-Trace-Id"));
        assertFalse(response.getHeader("X-Trace-Id").isBlank());
    }

    @Test
    @DisplayName("有 Tracer 时应复用链路 traceId/spanId")
    void shouldReuseTracerIds() throws Exception {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("trace-abc");
        when(context.spanId()).thenReturn("span-xyz");

        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracer);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(provider).doFilter(request, response, recordingChain);

        assertEquals("trace-abc", response.getHeader("X-Trace-Id"));
        assertEquals("span-xyz", response.getHeader("X-Span-Id"));
    }

    @Test
    @DisplayName("Tracer 无当前 Span 时应降级生成 ID")
    void shouldFallbackWhenNoCurrentSpan() throws Exception {
        Tracer tracer = mock(Tracer.class);
        when(tracer.currentSpan()).thenReturn(null);

        ObjectProvider<Tracer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tracer);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        new TraceIdFilter(provider).doFilter(request, response, recordingChain);

        assertNotNull(response.getHeader("X-Trace-Id"));
        assertFalse(response.getHeader("X-Trace-Id").isBlank());
    }

    @Test
    @DisplayName("下游抛异常时也应清理 MDC")
    void shouldCleanMdcOnChainError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };
        assertThrows(IllegalStateException.class,
                () -> new TraceIdFilter(emptyProvider()).doFilter(request, response, failingChain));
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
    }
}
