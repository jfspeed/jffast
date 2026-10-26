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
package cn.jfspeed.security.ratelimit;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.security.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * per-IP 限流单元测试（登录 / 验证码下发 / 验证码需求查询三个场景共用一份实现）。
 *
 * @author jffast
 */
class IpRateLimiterTest {

    private static SecurityProperties properties(int maxAttempts, int windowSeconds, List<String> trustedProxies) {
        SecurityProperties properties = new SecurityProperties();
        properties.getLoginIpLimit().setMaxAttempts(maxAttempts);
        properties.getLoginIpLimit().setWindowSeconds(windowSeconds);
        properties.setTrustedProxies(trustedProxies);
        return properties;
    }

    private static HttpServletRequest request(String remoteAddr, String xForwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        return request;
    }

    private static IpRateLimiter limiter(SecurityProperties properties) {
        return new IpRateLimiter(new ClientIpResolver(properties.getTrustedProxies()), properties);
    }

    @Test
    @DisplayName("未超过阈值时放行")
    void allowsWhenUnderThreshold() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(30L);

            assertDoesNotThrow(() -> limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
        }
    }

    @Test
    @DisplayName("超过阈值时抛 429 且用场景配置的文案")
    void rejectsWhenOverThreshold() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(31L);
            SecurityProperties properties = properties(30, 60, List.of());
            properties.getLoginIpLimit().setMessage("手速太快了");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> limiter(properties).check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertEquals(429, ex.getCode());
            assertEquals("手速太快了", ex.getMessage());
        }
    }

    @Test
    @DisplayName("关闭场景开关时完全不触碰 Redis")
    void skipsWhenDisabled() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            SecurityProperties properties = properties(30, 60, List.of());
            properties.getLoginIpLimit().setEnabled(false);

            limiter(properties).check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN);

            redis.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("分桶键按 TCP 对端地址（未配可信代理时忽略伪造的 X-Forwarded-For）")
    void keysOnRemoteAddrWhenNoTrustedProxy() {
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return 1L;
            });

            limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", "1.1.1.1"), IpRateLimiter.SCENE_LOGIN);

            assertEquals("jffast:security:ipLimit:login:203.0.113.9", keyRef.get());
        }
    }

    @Test
    @DisplayName("分桶键取 X-Forwarded-For 最右侧的非可信跳（伪造免疫）")
    void keysOnRightmostUntrustedHopWhenBehindTrustedProxy() {
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return 1L;
            });

            limiter(properties(30, 60, List.of("172.16.0.0/12")))
                    .check(request("172.16.0.5", "1.1.1.1, 203.0.113.9"), IpRateLimiter.SCENE_LOGIN);

            assertEquals("jffast:security:ipLimit:login:203.0.113.9", keyRef.get());
        }
    }

    @Test
    @DisplayName("仅在窗口内首次计数时设置过期时间")
    void setsExpireOnlyOnFirstAttempt() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(1L);
            limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN);
            redis.verify(() -> RedisUtil.expire(anyString(), eq(60L)), times(1));

            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(2L);
            limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN);
            // 第二次及以后不应重复设置，否则窗口会被无限顺延、计数永不过期
            redis.verify(() -> RedisUtil.expire(anyString(), eq(60L)), times(1));
        }
    }

    @Test
    @DisplayName("key 已存在但无 TTL 时补设过期，避免该 IP 被永久计数、永不解封")
    void reArmsTtlWhenKeyHasNoExpiry() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(2L);
            // -1 = 存在但无 TTL（incr 成功而 expire 失败 / 进程中断留下的残 key）
            redis.when(() -> RedisUtil.getExpire(anyString())).thenReturn(-1L);

            limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN);

            redis.verify(() -> RedisUtil.expire(anyString(), eq(60L)), times(1));
        }
    }

    @Test
    @DisplayName("key 有正常 TTL 时不重复设置过期（窗口不被顺延）")
    void keepsExistingTtlUntouched() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenReturn(2L);
            redis.when(() -> RedisUtil.getExpire(anyString())).thenReturn(45L);

            limiter(properties(30, 60, List.of()))
                    .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN);

            redis.verify(() -> RedisUtil.expire(anyString(), anyLong()), times(0));
        }
    }

    @Test
    @DisplayName("Redis 不可用时按拒绝处理（fail-closed），且文案与「真的太频繁」区分开")
    void failsClosedWhenRedisUnavailable() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenThrow(new RuntimeException("redis down"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> limiter(properties(30, 60, List.of()))
                            .check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertEquals(429, ex.getCode());
            assertEquals("服务暂时不可用，请稍后再试", ex.getMessage());
        }
    }

    @Test
    @DisplayName("连续调用按同一 key 累加，达到阈值后拒绝")
    void accumulatesOnSameKey() {
        AtomicLong counter = new AtomicLong();
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return counter.incrementAndGet();
            });

            IpRateLimiter limiter = limiter(properties(3, 60, List.of()));
            assertDoesNotThrow(() -> limiter.check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertDoesNotThrow(() -> limiter.check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertDoesNotThrow(() -> limiter.check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertThrows(BusinessException.class,
                    () -> limiter.check(request("203.0.113.9", null), IpRateLimiter.SCENE_LOGIN));
            assertEquals("jffast:security:ipLimit:login:203.0.113.9", keyRef.get());
        }
    }

    @Test
    @DisplayName("验证码场景独立分桶、独立阈值与文案")
    void captchaSceneIsIndependent() {
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return 11L;
            });
            SecurityProperties properties = properties(30, 60, List.of());
            properties.getCaptchaIpLimit().setMaxAttempts(10);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> limiter(properties).check(request("203.0.113.9", null), IpRateLimiter.SCENE_CAPTCHA));
            assertEquals(429, ex.getCode());
            assertEquals("验证码获取过于频繁，请稍后再试", ex.getMessage());
            // 与登录场景的 key 不同 ⇒ 登录打满不会连带锁死验证码，反之亦然
            assertEquals("jffast:security:ipLimit:captcha:203.0.113.9", keyRef.get());
        }
    }

    @Test
    @DisplayName("验证码需求查询场景独立分桶、独立阈值与文案")
    void captchaCheckSceneIsIndependent() {
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return 61L;
            });
            SecurityProperties properties = properties(30, 60, List.of());
            properties.getCaptchaCheckIpLimit().setMaxAttempts(60);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> limiter(properties).check(request("203.0.113.9", null), IpRateLimiter.SCENE_CAPTCHA_CHECK));
            assertEquals(429, ex.getCode());
            assertEquals("请求过于频繁，请稍后再试", ex.getMessage());
            // 与登录 / 验证码场景各自独立分桶
            assertEquals("jffast:security:ipLimit:captchaCheck:203.0.113.9", keyRef.get());
        }
    }

    @Test
    @DisplayName("取不到来源 IP 时落入固定兜底桶，而不是各自成桶绕过限流")
    void fallsBackToUnknownBucket() {
        AtomicReference<String> keyRef = new AtomicReference<>();
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.incr(anyString(), anyLong())).thenAnswer(invocation -> {
                keyRef.set(invocation.getArgument(0));
                return 1L;
            });

            limiter(properties(30, 60, List.of()))
                    .check(request(null, null), IpRateLimiter.SCENE_LOGIN);

            assertEquals("jffast:security:ipLimit:login:unknown", keyRef.get());
        }
    }

    @Test
    @DisplayName("未登记的场景直接抛异常，而不是静默跳过限流")
    void unknownSceneFailsLoudly() {
        try (MockedStatic<RedisUtil> redis = Mockito.mockStatic(RedisUtil.class)) {
            assertThrows(IllegalStateException.class,
                    () -> limiter(properties(30, 60, List.of()))
                            .check(request("203.0.113.9", null), "typo-scene"));
            redis.verifyNoInteractions();
        }
    }
}
