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
package cn.jfspeed.security.aspect;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.security.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 接口限流切面单元测试。
 *
 * @author jffast
 */
class RateLimitAspectTest {

    private final RateLimiterRegistry registry = mock(RateLimiterRegistry.class);
    private final RateLimitAspect aspect = new RateLimitAspect(registry);

    static class LimitedService {
        @RateLimit(value = "testLimit", fallbackResponseCode = 429)
        public String limited() {
            return "ok";
        }

        /** 显式给全阈值：应就地建实例，键为 {@code testLimit:3:10} */
        @RateLimit(value = "testLimit", maxCount = 3, seconds = 10)
        public String limitedWithThresholds() {
            return "ok";
        }

        /** 只给 maxCount、没给 seconds：视为「未指定」，仍走 yml 实例（避免半套参数静默收紧） */
        @RateLimit(value = "testLimit", maxCount = 5)
        public String limitedPartialThresholds() {
            return "ok";
        }

        /** 自定义文案与响应码 */
        @RateLimit(value = "testLimit", message = "手速太快了，歇会儿", fallbackResponseCode = 430)
        public String limitedWithMessage() {
            return "ok";
        }
    }

    private ProceedingJoinPoint joinPoint() throws Throwable {
        return joinPoint("limited");
    }

    private ProceedingJoinPoint joinPoint(String methodName) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(LimitedService.class.getMethod(methodName));
        when(signature.getDeclaringTypeName()).thenReturn(LimitedService.class.getName());
        return point;
    }

    private RateLimit annotationOf(String methodName) throws NoSuchMethodException {
        return LimitedService.class.getMethod(methodName).getAnnotation(RateLimit.class);
    }

    @Test
    @DisplayName("拿到许可时放行并返回目标方法结果")
    void proceedsWhenPermissionGranted() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(anyString())).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(true);
        ProceedingJoinPoint point = joinPoint();
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point, LimitedService.class.getMethod("limited").getAnnotation(RateLimit.class)));
        verify(point).proceed();
    }

    @Test
    @DisplayName("拿不到许可时抛 fallbackResponseCode 业务异常")
    void throwsWhenPermissionDenied() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(anyString())).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(false);
        ProceedingJoinPoint point = joinPoint();

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(point, LimitedService.class.getMethod("limited").getAnnotation(RateLimit.class)));
        assertEquals(429, ex.getCode());
    }

    @Test
    @DisplayName("未显式给阈值时按 value 取 yml 注册表实例（不就地建实例）")
    void usesRegistryInstanceWhenThresholdsUnspecified() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(eq("testLimit"))).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(true);
        ProceedingJoinPoint point = joinPoint();
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point, annotationOf("limited")));

        verify(registry).rateLimiter("testLimit");
        verify(registry, never()).rateLimiter(anyString(), any(RateLimiterConfig.class));
    }

    @Test
    @DisplayName("显式给全阈值时就地建实例，键为 value:maxCount:seconds")
    void buildsInlineInstanceWhenThresholdsGiven() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(eq("testLimit:3:10"), any(RateLimiterConfig.class))).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(true);
        ProceedingJoinPoint point = joinPoint("limitedWithThresholds");
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point, annotationOf("limitedWithThresholds")));

        verify(registry).rateLimiter(eq("testLimit:3:10"), any(RateLimiterConfig.class));
        verify(registry, never()).rateLimiter("testLimit");
    }

    @Test
    @DisplayName("只给 maxCount 不给 seconds 视为未指定，仍走 yml 实例")
    void fallsBackToRegistryWhenThresholdsPartial() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(eq("testLimit"))).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(true);
        ProceedingJoinPoint point = joinPoint("limitedPartialThresholds");
        when(point.proceed()).thenReturn("ok");

        assertEquals("ok", aspect.around(point, annotationOf("limitedPartialThresholds")));

        verify(registry).rateLimiter("testLimit");
        verify(registry, never()).rateLimiter(anyString(), any(RateLimiterConfig.class));
    }

    @Test
    @DisplayName("被限流时使用注解上的 message 与 fallbackResponseCode")
    void usesCustomMessageAndCodeWhenLimited() throws Throwable {
        RateLimiter limiter = mock(RateLimiter.class);
        when(registry.rateLimiter(eq("testLimit"))).thenReturn(limiter);
        when(limiter.acquirePermission()).thenReturn(false);
        ProceedingJoinPoint point = joinPoint("limitedWithMessage");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> aspect.around(point, annotationOf("limitedWithMessage")));
        assertEquals(430, ex.getCode());
        assertEquals("手速太快了，歇会儿", ex.getMessage());
        verify(point, never()).proceed();
    }
}
