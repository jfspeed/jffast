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

import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.core.exception.BusinessException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * @author jffast
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterRegistry rateLimiterRegistry;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint point, RateLimit rateLimit) throws Throwable {
        RateLimiter rateLimiter = resolveLimiter(rateLimit);

        MethodSignature signature = (MethodSignature) point.getSignature();

        if (!rateLimiter.acquirePermission()) {
            log.warn("接口限流触发: {}.{}", signature.getDeclaringTypeName(), signature.getMethod().getName());
            throw new BusinessException(rateLimit.fallbackResponseCode(), rateLimit.message());
        }

        return point.proceed();
    }

    /**
     * 取限流器：注解显式给了阈值（{@code maxCount} 与 {@code seconds} 都 &gt; 0）就按注解值就地建实例；
     * 否则按 {@code value()} 取 yml 注册表实例（运维可调，不用改代码）。
     * <p>
     * 显式实例用 {@code value:maxCount:seconds} 作键：一是与 yml 同名实例区分开（resilience4j 的
     * registry 同名已存在时会忽略新配置，直接传 {@code value} 会让注解值形同无效）；二是不同阈值
     * 天然隔离，不会互相覆盖；三是键的取值有限（阈值都写在代码里），不会像按 IP 拼键那样无限膨胀。
     */
    private RateLimiter resolveLimiter(RateLimit rateLimit) {
        int maxCount = rateLimit.maxCount();
        int seconds = rateLimit.seconds();
        if (maxCount <= 0 || seconds <= 0) {
            return rateLimiterRegistry.rateLimiter(rateLimit.value());
        }
        String key = rateLimit.value() + ':' + maxCount + ':' + seconds;
        return rateLimiterRegistry.rateLimiter(key, RateLimiterConfig.custom()
                .limitForPeriod(maxCount)
                .limitRefreshPeriod(Duration.ofSeconds(seconds))
                .timeoutDuration(Duration.ZERO)
                .build());
    }
}