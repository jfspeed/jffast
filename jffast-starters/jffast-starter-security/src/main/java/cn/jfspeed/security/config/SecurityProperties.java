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
package cn.jfspeed.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全模块配置属性。
 * <p>
 * {@code auth-excludes} 为业务方追加的免登录白名单（登录校验拦截器 excludePathPatterns）。
 * 框架内置白名单只保留基础设施路径；业务/扩展模块自身的免登录端点（如 OAuth2 授权流程、
 * 开放接口前缀等）应通过本配置显式声明，谁启用谁放行，避免框架层预置宽泛的匿名前缀。
 * <p>
 * 注意：配置进白名单的路径即跳过登录校验，必须由业务侧自行保证鉴权
 * （如独立拦截器、注解校验），否则为匿名访问。
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.security")
public class SecurityProperties {

    /** 追加的免登录路径（Ant 风格），如 /oauth2/client/authorize/** */
    private List<String> authExcludes = new ArrayList<>();

    /**
     * Sa-Token session 中存储的业务对象类型白名单（全限定类名）。
     * <p>
     * Sa-Token 1.46.0 起，JSON 多态反序列化（Jackson DefaultTyping）受全局类型白名单约束
     * （{@link cn.dev33.satoken.strategy.SaJsonStrategy}，修复 DefaultTyping RCE）。
     * 登录时写入 session 的业务实体（如用户/角色对象）必须在启动时注册，
     * 否则读取 session 时反序列化被拒，表现为「已登录但取不到用户」。
     * 各应用按自身 session 实际存储的类型配置，禁止注册 Object.class（等于全开）。
     */
    private List<String> saJsonAllowTypes = new ArrayList<>();

    /**
     * 可信反向代理的 IP / CIDR 列表，如 {@code 127.0.0.1}、{@code 172.16.0.0/12}。
     * <p>
     * 只有 TCP 对端地址命中本列表时，才按 {@code X-Forwarded-For} 逐跳回溯真实客户端 IP；
     * 否则一律使用 TCP 对端地址（**完全不读转发头**）。默认为空 ⇒ 不信任任何转发头。
     * <p>
     * 这是安全默认值而非省事默认值：{@code X-Forwarded-For} 是客户端可控的请求头，
     * 无条件信任等于把「按 IP 限流 / IP 白名单」变成客户端一句话就能绕过的摆设。
     * 反向代理部署（本项目自带 nginx：前端容器反代后端）下必须把代理地址/段填进来，
     * 否则所有用户会被判定成同一个 IP（代理自己的地址）而共用配额。
     */
    private List<String> trustedProxies = new ArrayList<>();

    /** 登录接口 per-IP 限流配置 */
    private LoginIpLimit loginIpLimit = new LoginIpLimit();

    /** 图形验证码下发接口 per-IP 限流配置 */
    private CaptchaIpLimit captchaIpLimit = new CaptchaIpLimit();

    /** 「是否需要验证码」查询接口 per-IP 限流配置 */
    private CaptchaCheckIpLimit captchaCheckIpLimit = new CaptchaCheckIpLimit();

    /**
     * 各 per-IP 限流场景共用的参数形状。
     * <p>
     * 抽成接口是为了让 {@code IpRateLimiter} 用一份实现覆盖多个场景（登录、验证码…）：
     * 阈值/文案/开关各不相同，但「按 IP 分桶 + 窗口计数」的逻辑完全一致，
     * 复制两份实现只会让两边行为悄悄漂移。
     */
    public interface IpLimitSettings {

        /** 是否启用 */
        boolean isEnabled();

        /** 窗口内单 IP 允许的次数 */
        int getMaxAttempts();

        /** 窗口长度（秒） */
        int getWindowSeconds();

        /** 被限流时的提示文案 */
        String getMessage();
    }

    /**
     * 登录接口按客户端 IP 的独立限流。
     * <p>
     * 存在的理由：{@code @RateLimit} 用的是 resilience4j 全局共享令牌桶，**没有 IP 维度**——
     * 登录上挂的 {@code strictApi}（5 次/秒）是全站共用的桶，单个攻击者打满它就能让
     * 所有人登录 429（防护反而变成可用性问题）。本限流按客户端 IP 分桶，把「一个 IP 的配额」
     * 和「全站总量」解耦，两者叠加：per-IP 挡暴破，全局桶兜总量。
     * <p>
     * 计数口径：**统计全部登录尝试**（成功与失败都计）。只统计失败的话，撞库一旦蒙对就清零重来，
     * 等于给攻击者开后门；而 30 次/分钟对真实用户（含打错密码几次）绰绰有余。
     */
    @Data
    public static class LoginIpLimit implements IpLimitSettings {

        /** 是否启用，默认 true */
        private boolean enabled = true;

        /**
         * 窗口内单 IP 允许的登录尝试次数，默认 30。
         * <p>
         * 注意 NAT / 统一出口场景：整层楼共用一个公网 IP 时，早高峰集中登录可能触顶。
         * 这种场景应先把出口代理配进 {@code trusted-proxies}（拿到真实内网 IP 分桶），
         * 仍不够再调大本值 —— 不要一上来就放宽，否则限流形同虚设。
         */
        private int maxAttempts = 30;

        /** 窗口长度（秒），默认 60 */
        private int windowSeconds = 60;

        /** 被限流时的提示文案 */
        private String message = "登录尝试过于频繁，请稍后再试";
    }

    /**
     * 图形验证码下发（{@code GET /sys/getCode}）的 per-IP 限流。
     * <p>
     * 该接口**匿名可达**且每次调用都往 Redis 写一个带 60 秒 TTL 的 key（key 由前端传入），
     * 不限流就是一个零成本的 Redis 写入放大点：攻击者用随机 key 反复调用，
     * 既能耗尽 Redis 内存，也能把「2 次失败后必须填验证码」这道防线变成噪声。
     * <p>
     * 阈值显著小于登录：正常用户一次登录最多取一两次验证码，10 次/分钟已足够宽松。
     */
    @Data
    public static class CaptchaIpLimit implements IpLimitSettings {

        /** 是否启用，默认 true */
        private boolean enabled = true;

        /** 窗口内单 IP 允许的验证码获取次数，默认 10 */
        private int maxAttempts = 10;

        /** 窗口长度（秒），默认 60 */
        private int windowSeconds = 60;

        /** 被限流时的提示文案 */
        private String message = "验证码获取过于频繁，请稍后再试";
    }

    /**
     * 「是否需要验证码」查询（{@code GET /sys/needCaptcha}）的 per-IP 限流。
     * <p>
     * 该接口**匿名可达**且入参 {@code userName} 由调用方自由指定：不加限制时，
     * 攻击者可拿一份用户名清单反复轮询，把返回值当作「该账号是否已被多次尝试」的生存探测
     * （响应只为布尔值，但足以区分「从未失败」与「已被连续尝试」）。
     * <p>
     * 阈值比登录宽松：前端每次进入登录页 / 提交前都可能调一次，正常用户远达不到；
     * 取 60 次/分钟既不影响正常交互，又能把批量轮询压到近乎无效。
     */
    @Data
    public static class CaptchaCheckIpLimit implements IpLimitSettings {

        /** 是否启用，默认 true */
        private boolean enabled = true;

        /** 窗口内单 IP 允许的查询次数，默认 60 */
        private int maxAttempts = 60;

        /** 窗口长度（秒），默认 60 */
        private int windowSeconds = 60;

        /** 被限流时的提示文案 */
        private String message = "请求过于频繁，请稍后再试";
    }
}
