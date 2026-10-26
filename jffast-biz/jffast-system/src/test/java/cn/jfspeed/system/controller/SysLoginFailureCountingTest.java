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
package cn.jfspeed.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.security.ratelimit.IpRateLimiter;

/**
 * 登录失败计数的判定口径（无需 Redis/MySQL）。
 * <p>
 * 第三轮复核实测出的两处坑都在这里钉住：
 * <ol>
 *   <li>「是否已锁定」必须按 {@code >=} 判。原来按 {@code ==} 判等，而 {@code hincr} 在并发下会跳号、
 *       运维下调阈值也会让存量计数直接越过上限——两种情况都永不命中，锁定提示与计数重置双双失效，
 *       每失败一次还把解锁时间往后推，合法用户被永久锁死；</li>
 *   <li>存在性判断不能走 {@code RedisUtil.hasKey}：它吞掉连接异常返回 false，于是 Redis 抖一下
 *       就等于「本轮不需要验证码」，而这正是暴破要挑的窗口。</li>
 * </ol>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysLoginFailureCountingTest {

    private static final String USER_NAME = "tester";
    private static final String COUNT_KEY = BaseConstant.CACHE_ERROR_COUNT + USER_NAME;
    private static final int MAX = 3;

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;
    @Mock
    private ObjectProvider<IpRateLimiter> ipRateLimiter;
    @Mock
    private ObjectProvider<ClientIpResolver> clientIpResolver;

    @InjectMocks
    private SysLoginController controller;

    private MockedStatic<RedisUtil> redis;

    @BeforeEach
    void setUp() {
        redis = mockStatic(RedisUtil.class);
    }

    @AfterEach
    void tearDown() {
        redis.close();
    }

    @Test
    @DisplayName("达到上限即算锁定：等于、超过都命中（旧实现用 == 判等，超过时永不命中）")
    void reachingOrOvershootingLimitCountsAsLocked() {
        assertTrue(SysLoginController.reachedLimit(MAX, MAX), "等于上限必须命中");
        assertTrue(SysLoginController.reachedLimit(MAX + 3, MAX), "并发跳号/阈值下调后必须仍然命中");
        assertFalse(SysLoginController.reachedLimit(MAX - 1, MAX), "未到上限不得命中");
    }

    @Test
    @DisplayName("无计数时读作 0，而不是抛 NPE")
    void missingCounterReadsZero() {
        redis.when(() -> RedisUtil.hget(eq(COUNT_KEY), eq("errorNum"))).thenReturn(null);

        assertEquals(0, invokeCurrentErrorNum());
    }

    @Test
    @DisplayName("计数读取以 hget 为准：Redis 故障时向上抛，绝不退化成「不需要验证码」")
    void redisFailurePropagatesInsteadOfSilentlySkippingCaptcha() {
        redis.when(() -> RedisUtil.hget(anyString(), anyString()))
                .thenThrow(new IllegalStateException("Unable to connect to Redis"));

        assertThrows(IllegalStateException.class, this::invokeCurrentErrorNum);
    }

    @Test
    @DisplayName("锁定窗口内且已达上限：拒绝登录并给出剩余分钟")
    void lockedInsideWindowIsRejected() {
        long future = System.currentTimeMillis() + 600_000L;
        stubCounter(MAX, future);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> ReflectionTestUtils.invokeMethod(controller, "lockedUser",
                        System.currentTimeMillis(), USER_NAME, MAX));

        assertTrue(ex.getMessage().contains("账号锁定中"), "实际提示：" + ex.getMessage());
    }

    @Test
    @DisplayName("计数越过上限且窗口已过：放行给重置分支（永久锁死就是这一步被 == 卡住）")
    void overshootAfterWindowIsReleasedForReset() {
        long past = System.currentTimeMillis() - 1_000L;
        stubCounter(MAX + 4, past);

        Boolean proceed = ReflectionTestUtils.invokeMethod(controller, "lockedUser",
                System.currentTimeMillis(), USER_NAME, MAX);

        assertEquals(Boolean.TRUE, proceed, "越过上限的旧计数在窗口过后必须能重新起算");
    }

    @Test
    @DisplayName("只有半个字段（errorNum 在、loginTime 缺）不得抛 NPE，按未锁定处理")
    void halfWrittenCounterIsTolerated() {
        redis.when(() -> RedisUtil.hget(eq(COUNT_KEY), eq("errorNum"))).thenReturn(2);
        redis.when(() -> RedisUtil.hget(eq(COUNT_KEY), eq("loginTime"))).thenReturn(null);

        Boolean proceed = ReflectionTestUtils.invokeMethod(controller, "lockedUser",
                System.currentTimeMillis(), USER_NAME, MAX);

        assertEquals(Boolean.FALSE, proceed, "计数字段不全时不得当成「已建立计数」继续累加");
    }

    private void stubCounter(Object errorNum, Object loginTime) {
        redis.when(() -> RedisUtil.hget(eq(COUNT_KEY), eq("errorNum"))).thenReturn(errorNum);
        redis.when(() -> RedisUtil.hget(eq(COUNT_KEY), eq("loginTime"))).thenReturn(loginTime);
    }

    private int invokeCurrentErrorNum() {
        Integer value = ReflectionTestUtils.invokeMethod(controller, "currentErrorNum", COUNT_KEY);
        return value == null ? -1 : value;
    }
}
