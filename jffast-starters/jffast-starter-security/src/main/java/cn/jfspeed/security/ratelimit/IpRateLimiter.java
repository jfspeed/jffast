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

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.security.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按客户端 IP 的独立限流，支持多个场景（登录、验证码…）共用一份实现。
 * <p>
 * 与 {@code @RateLimit}（resilience4j 全局共享令牌桶）分工：
 * <ul>
 *   <li>全局桶：兜住全站总量，防止接口被打垮；</li>
 *   <li>本限流：按客户端 IP 分桶，让「一个 IP 打满配额」不会波及其他人 —— 这正是纯全局桶的失效模式。</li>
 * </ul>
 * 两者叠加而非互相替代。
 * <p>
 * 为什么按「场景」而不是按「一个类一个场景」：阈值/文案/开关各不相同，但
 * 「按 IP 分桶 + 窗口计数」的逻辑完全一致；复制两份实现只会让两边行为悄悄漂移
 * （例如一边修了 fail-closed、另一边忘了）。新增场景只需在 {@link SecurityProperties}
 * 加一个配置块并在此登记，控制层用 {@link #SCENE_LOGIN} 这类常量调用，编译期即可发现拼错。
 * <p>
 * <b>失效策略</b>：Redis 不可用时<b>拒绝</b>（fail-closed）。这些场景本身已依赖 Redis
 * （登录的验证码/失败计数），因此拒绝不额外降低可用性；反过来若 fail-open，
 * 则「Redis 挂掉」恰好是攻击者最想等的窗口。
 *
 * @author jffast
 */
@Slf4j
public class IpRateLimiter {

    /** 登录场景：{@code POST /sys/login} */
    public static final String SCENE_LOGIN = "login";

    /** 验证码场景：{@code GET /sys/getCode} */
    public static final String SCENE_CAPTCHA = "captcha";

    /** 验证码需求查询场景：{@code GET /sys/needCaptcha} */
    public static final String SCENE_CAPTCHA_CHECK = "captchaCheck";

    /** 计数 key 前缀，命名遵循「{业务}:{对象}:{标识}」规范 */
    private static final String KEY_PREFIX = BaseConstant.CACHE_PREFIX + "security:ipLimit:";

    /** 限流判定依赖的缓存不可用时的响应码与文案（与「真的太频繁」区分开，便于运维定位） */
    private static final int UNAVAILABLE_CODE = 429;
    private static final String UNAVAILABLE_MESSAGE = "服务暂时不可用，请稍后再试";

    private final ClientIpResolver clientIpResolver;
    private final Map<String, SecurityProperties.IpLimitSettings> scenes;

    public IpRateLimiter(ClientIpResolver clientIpResolver, SecurityProperties securityProperties) {
        this.clientIpResolver = clientIpResolver;
        SecurityProperties properties = securityProperties == null ? new SecurityProperties() : securityProperties;
        Map<String, SecurityProperties.IpLimitSettings> map = new LinkedHashMap<>();
        map.put(SCENE_LOGIN, properties.getLoginIpLimit());
        map.put(SCENE_CAPTCHA, properties.getCaptchaIpLimit());
        map.put(SCENE_CAPTCHA_CHECK, properties.getCaptchaCheckIpLimit());
        this.scenes = Collections.unmodifiableMap(map);
    }

    /**
     * 校验当前 IP 在该场景下的频率，超限抛 {@link BusinessException}。
     *
     * @param request 当前请求
     * @param scene   场景常量（{@link #SCENE_LOGIN} / {@link #SCENE_CAPTCHA} / {@link #SCENE_CAPTCHA_CHECK}）
     */
    public void check(HttpServletRequest request, String scene) {
        SecurityProperties.IpLimitSettings rule = scenes.get(scene);
        if (rule == null) {
            // 场景是编译期常量，走到这里说明有人新增了调用却没登记 —— 属于编程错误，
            // 宁可炸出来也不能静默跳过（静默跳过 = 该场景悄悄没有限流）
            throw new IllegalStateException("未登记的 per-IP 限流场景: " + scene);
        }
        if (!rule.isEnabled()) {
            return;
        }
        String clientIp = clientIpResolver.resolve(request);
        if (clientIp == null || clientIp.isEmpty()) {
            // 拿不到 IP（极端情况）时用固定兜底桶，保证「所有无法识别来源的请求」仍被统一计数，
            // 而不是各自成桶绕过限流
            clientIp = "unknown";
        }

        String key = KEY_PREFIX + scene + ':' + clientIp;
        long attempts;
        try {
            attempts = RedisUtil.incr(key, 1);
            if (attempts == 1) {
                // 仅首次计数时设过期：窗口随首次尝试滑动，避免「固定窗口边界」被踩点，
                // 也避免每次请求都顺延 TTL 导致计数永不过期
                RedisUtil.expire(key, Math.max(1, rule.getWindowSeconds()));
            } else if (RedisUtil.getExpire(key) < 0) {
                // 兜底：key 已存在但没有任何 TTL（incr 成功而 expire 失败，或两次调用之间进程中断
                // 留下的残 key）。不补设则该桶被永久计数、永不解封 —— 该 IP 一旦踩过阈值就再也登不进来。
                // 只在「确认无 TTL」时补设一次，不改「窗口随首次尝试滑动」的既有语义。
                RedisUtil.expire(key, Math.max(1, rule.getWindowSeconds()));
            }
        } catch (Exception e) {
            log.error("场景 {} 的 IP 限流读写 Redis 失败，按拒绝处理（fail-closed），IP：{}", scene, clientIp, e);
            throw new BusinessException(UNAVAILABLE_CODE, UNAVAILABLE_MESSAGE);
        }

        if (attempts > rule.getMaxAttempts()) {
            log.warn("场景 {} 中客户端 IP {} 在 {} 秒内尝试 {} 次，超过阈值 {} 次，已拒绝",
                    scene, clientIp, rule.getWindowSeconds(), attempts, rule.getMaxAttempts());
            throw new BusinessException(UNAVAILABLE_CODE, rule.getMessage());
        }
    }
}
