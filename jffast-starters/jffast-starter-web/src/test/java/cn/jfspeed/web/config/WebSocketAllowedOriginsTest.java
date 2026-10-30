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
package cn.jfspeed.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 跨源放行名单的取值契约测试。
 *
 * <p>锁死 2026-10-29 的收紧语义：<b>名单留空 = 只放行同源</b>。
 * 此前把空串映射成 {@code "*"}，等于默认向任意站点开放握手 —— Spring 的
 * {@code OriginHandshakeInterceptor} 对跨源请求取 {@code CorsConfiguration.checkOrigin}，
 * 名单含 {@code "*"} 即无条件放行。本测试同时用 Spring 真实的 CorsConfiguration
 * 验证「名单为空 ⇒ 跨源一律被拒」，避免只测字符串拼接而漏掉框架侧语义。
 *
 * @author jffast
 */
@DisplayName("WebSocket 跨源放行名单契约测试")
class WebSocketAllowedOriginsTest {

    @Test
    @DisplayName("未配置 / 空白 ⇒ 空名单（不再隐式放行 *）")
    void blankMeansNoCrossOriginAllowed() {
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins(null)).isEmpty();
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins("")).isEmpty();
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins("   ")).isEmpty();
    }

    @Test
    @DisplayName("名单为空时不得出现 * —— 旧实现把空串映射成 *，等于默认向任意站点开放握手")
    void emptyListNeverImpliesWildcard() {
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins(""))
                .doesNotContain("*");
    }

    @Test
    @DisplayName("逗号分隔：去空白、丢空项、保留顺序")
    void parsesCommaSeparatedList() {
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins("https://a.example.com"))
                .containsExactly("https://a.example.com");
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins(
                " https://a.example.com , ,https://b.example.com "))
                .containsExactly("https://a.example.com", "https://b.example.com");
    }

    @Test
    @DisplayName("显式 * 仍可放行任意来源（仅限本地联调）")
    void explicitWildcardIsPreserved() {
        assertThat(WebSocketAutoConfiguration.parseAllowedOrigins("*")).containsExactly("*");
    }

    @Test
    @DisplayName("Spring 侧语义：空名单 ⇒ 跨源握手被拒；名单内的来源被放行")
    void springCorsConfigurationRejectsCrossOriginWhenListIsEmpty() {
        CorsConfiguration empty = new CorsConfiguration();
        empty.setAllowedOrigins(Arrays.asList(WebSocketAutoConfiguration.parseAllowedOrigins("")));
        assertThat(empty.checkOrigin("http://evil.example.com")).isNull();

        CorsConfiguration listed = new CorsConfiguration();
        listed.setAllowedOrigins(Arrays.asList(
                WebSocketAutoConfiguration.parseAllowedOrigins("https://a.example.com")));
        assertThat(listed.checkOrigin("https://a.example.com")).isEqualTo("https://a.example.com");
        assertThat(listed.checkOrigin("http://evil.example.com")).isNull();
    }

    @Test
    @DisplayName("日志文案：空名单标注「仅同源」，非空打印实际名单")
    void describesPolicy() {
        assertThat(WebSocketAutoConfiguration.describeOrigins(new String[0])).contains("仅同源");
        List<String> origins = Arrays.asList(WebSocketAutoConfiguration.parseAllowedOrigins(
                "https://a.example.com,https://b.example.com"));
        assertThat(WebSocketAutoConfiguration.describeOrigins(origins.toArray(new String[0])))
                .contains("https://a.example.com", "https://b.example.com");
    }
}
