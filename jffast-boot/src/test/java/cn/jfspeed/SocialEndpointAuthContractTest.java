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
 * 配置契约：社交账号绑定的端点按「是否需要登录态」分成两组，不能混。
 *
 * <p>分组依据 2026-10-27 的链路改造：第三方平台按 {@code redirect-uri} 回跳到的是**前端中转页**
 * （{@code /social/callback}），授权码只能在中转页里取出，真正兑换授权码的是**发起绑定的那个
 * 已登录窗口**调后端 {@code GET /social/callback}。于是：
 *
 * <ul>
 *   <li>{@code /social/authorize}（跳第三方）仍必须**匿名可达** —— 它发生在绑定之前，
 *       用户此刻还没有登录态；</li>
 *   <li>{@code /social/callback}（兑换授权码、签发绑定票据）必须**受登录态保护** ——
 *       匿名放行等于把一张「与用户无关」的绑定票据发给任何拿到 code+state 的人
 *       （票据里 userId 为空 ⇒ bind 侧的「票据与当前登录用户不匹配」校验形同虚设），
 *       还会把已绑定的 userId 回吐给匿名调用方；</li>
 *   <li>{@code bind} / {@code unbind} / {@code binds} 是个人维度的绑定管理，同样需登录态。</li>
 * </ul>
 *
 * <p>安全性：社交控制器整体带 {@code @ConditionalOnProperty(jffast.social.enabled=true)}，
 * 未启用时根本不注册 ⇒ 排除 {@code /social/authorize} 不会暴露任何东西。
 *
 * @author jffast
 */
class SocialEndpointAuthContractTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resource(String path) throws IOException {
        try (InputStream in = SocialEndpointAuthContractTest.class.getResourceAsStream(path)) {
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
    @DisplayName("社交账号绑定的 authorize 必须匿名可达")
    void socialAuthorizeIsAnonymous() throws IOException {
        List<String> excludes = authExcludes();
        assertTrue(excludes.contains("/social/authorize"),
                "/social/authorize 必须免登录：跳第三方授权发生在绑定之前，否则整条绑定链 401。实际：" + excludes);
    }

    @Test
    @DisplayName("社交账号兑换授权码的 callback 必须受登录态保护")
    void socialCallbackRequiresLogin() throws IOException {
        List<String> excludes = authExcludes();
        assertFalse(excludes.contains("/social/callback"),
                "/social/callback 兑换一次性授权码并签发绑定票据，匿名放行等于把票据发给任何拿到 code+state 的人"
                        + "（票据 userId 为空 ⇒ bind 侧的用户匹配校验失效），还会把已绑定的 userId 回吐给匿名调用方。实际：" + excludes);
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
