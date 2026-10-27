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
package cn.jfspeed;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import cn.jfspeed.social.controller.SocialLoginController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.yaml.snakeyaml.Yaml;

/**
 * 配置契约：社交登录的「登录前」端点必须匿名可达，「登录后」端点必须仍受登录态保护。
 *
 * <p>背景（2026-10-26 开源前复核）：{@code SocialLoginController} 的
 * {@code /social/authorize}（跳第三方）与 {@code /social/callback}（第三方回跳）发生在
 * 登录之前，浏览器此时**没有**登录态；而 {@code jffast.security.auth-excludes} 里既没有
 * {@code PUBLIC_PATHS} 也没有这两个路径 ⇒ {@code SaInterceptor} 的 {@code StpUtil.checkLogin()}
 * 直接抛 401 ⇒ 整条社交登录链完全不可用（实测 {@code GET /social/authorize} → HTTP 401）。
 *
 * <p>同时钉住另一半：{@code bind} / {@code unbind} / {@code binds} 是个人维度的绑定管理，
 * 必须**仍需登录态**，不能因为「社交端点要匿名」而被一起排除。
 *
 * <p>安全性：社交控制器整体带 {@code @ConditionalOnProperty(jffast.social.enabled=true)}，
 * 未启用时根本不注册 ⇒ 排除这两个路径不会暴露任何东西。
 *
 * @author jffast
 */
class SocialAnonymousEndpointContractTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resource(String path) throws IOException {
        try (InputStream in = SocialAnonymousEndpointContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "缺少资源：" + path);
            return new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> authExcludes() throws IOException {
        Map<String, Object> root = resource("/application.yml");
        Map<String, Object> security = (Map<String, Object>) ((Map<String, Object>) root.get("jffast")).get("security");
        assertNotNull(security, "缺少配置节：jffast.security");
        return (List<String>) security.get("auth-excludes");
    }

    @Test
    @DisplayName("社交登录的 authorize / callback 必须匿名可达")
    void socialLoginBeforeEndpointsAreAnonymous() throws IOException {
        List<String> excludes = authExcludes();
        assertTrue(excludes.contains("/social/authorize"),
                "/social/authorize 必须免登录：跳第三方授权发生在登录前，否则整条社交登录链 401。实际：" + excludes);
        assertTrue(excludes.contains("/social/callback"),
                "/social/callback 必须免登录：第三方回跳时浏览器无登录态，否则回调恒 401。实际：" + excludes);
    }

    @Test
    @DisplayName("社交账号绑定管理端点不得被匿名放行")
    void socialBindingEndpointsStillRequireLogin() throws IOException {
        List<String> excludes = authExcludes();
        assertFalse(excludes.contains("/social/**"),
                "/social/** 整段放行会把 bind / unbind / binds 一并匿名化 —— 那等于任何人都能解绑他人账号");
        assertFalse(excludes.contains("/social/bind"),
                "/social/bind 是登录后的个人绑定操作，必须仍受登录态保护");
        assertFalse(excludes.contains("/social/binds"),
                "/social/binds 是登录后的个人绑定查询，必须仍受登录态保护");
        assertFalse(excludes.stream().anyMatch(p -> p.startsWith("/social/unbind")),
                "/social/unbind/** 是登录后的个人解绑操作，必须仍受登录态保护");
    }

    @Test
    @DisplayName("社交控制器由开关控制，未启用时不注册")
    void socialControllerIsConditionalOnEnableSwitch() {
        ConditionalOnProperty condition =
                SocialLoginController.class.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition, "社交控制器必须带 @ConditionalOnProperty，"
                + "否则 social.enabled=false 时匿名排除项会指向一个真实存在的端点");
        assertTrue(List.of(condition.name()).contains("jffast.social.enabled"),
                "条件开关名必须为 jffast.social.enabled");
        assertTrue("true".equals(condition.havingValue()),
                "社交端点只在显式开启时注册，默认关闭");
    }
}
