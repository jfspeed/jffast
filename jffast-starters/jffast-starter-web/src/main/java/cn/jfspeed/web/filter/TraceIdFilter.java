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

import cn.jfspeed.common.core.util.IdUtils;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TraceId 过滤器。
 *
 * <p>优先复用 Micrometer Tracing 产生的 traceId/spanId；若当前链路未开启，则降级生成 ULID。
 * 将 traceId/spanId 写入 MDC 与响应头，方便日志关联与前端排障。</p>
 *
 * @author jffast
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceIdFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        String spanId = request.getHeader(SPAN_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = currentTraceId().orElseGet(IdUtils::ulid);
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = currentSpanId().orElseGet(IdUtils::ulid);
        }

        MDC.put(TRACE_ID_KEY, traceId);
        MDC.put(SPAN_ID_KEY, spanId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
        }
    }

    private java.util.Optional<String> currentTraceId() {
        return extractFromTracer(TraceContext::traceId);
    }

    private java.util.Optional<String> currentSpanId() {
        return extractFromTracer(TraceContext::spanId);
    }

    private java.util.Optional<String> extractFromTracer(java.util.function.Function<TraceContext, String> extractor) {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return java.util.Optional.empty();
        }
        Span span = tracer.currentSpan();
        if (span == null || span.context() == null) {
            return java.util.Optional.empty();
        }
        String value = extractor.apply(span.context());
        return value == null || value.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(value);
    }
}
