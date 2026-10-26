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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Sa-Token 免登录白名单装配单元测试。
 *
 * <p>覆盖两条易回归的装配行为：① swagger 开关决定文档路径是否放行；
 * ② 业务追加的 {@code auth-excludes} 必须排在内置白名单之前（冲突时优先生效）。
 *
 * @author jffast
 */
class SaTokenConfigTest {

    @Test
    @DisplayName("swagger 开启时文档路径进入免登录白名单")
    void shouldIncludeSwaggerPathsWhenEnabled() throws Exception {
        List<String> excludes = excludesOf(new SecurityProperties(), true);
        assertTrue(excludes.contains("/doc.html"));
        assertTrue(excludes.contains("/v3/api-docs/**"));
        // Boot 4 / springdoc 原生 UI 路径
        assertTrue(excludes.contains("/swagger-ui.html"));
        assertTrue(excludes.contains("/swagger-ui/**"));
    }

    @Test
    @DisplayName("swagger 关闭时文档路径不进入白名单")
    void shouldExcludeSwaggerPathsWhenDisabled() throws Exception {
        List<String> excludes = excludesOf(new SecurityProperties(), false);
        assertFalse(excludes.contains("/doc.html"));
        assertFalse(excludes.contains("/v3/api-docs/**"));
        assertFalse(excludes.contains("/swagger-ui.html"));
        assertFalse(excludes.contains("/swagger-ui/**"));
    }

    @Test
    @DisplayName("基础设施白名单（上传 / WebSocket / error）始终放行")
    void shouldAlwaysExcludeInfrastructurePaths() throws Exception {
        List<String> excludes = excludesOf(new SecurityProperties(), false);
        assertTrue(excludes.contains("/upload/**"));
        assertTrue(excludes.contains("/ws/**"));
        assertTrue(excludes.contains("/error"));
    }

    @Test
    @DisplayName("业务追加的免登录路径排在白名单最前（冲突时优先）")
    void shouldPutConfiguredExcludesFirst() throws Exception {
        SecurityProperties props = new SecurityProperties();
        props.setAuthExcludes(List.of("/oauth2/client/authorize/**"));

        List<String> excludes = excludesOf(props, false);
        assertEquals("/oauth2/client/authorize/**", excludes.get(0));
    }

    @SuppressWarnings("unchecked")
    private List<String> excludesOf(SecurityProperties props, boolean swaggerEnabled) throws Exception {
        // 单体形态下容器里没有 InternalCallDetector：mock 出「取不到实例」的 ObjectProvider
        ObjectProvider<InternalCallDetector> detectorProvider = mock(ObjectProvider.class);
        SaTokenConfig config = new SaTokenConfig(props, detectorProvider, swaggerEnabled);

        // excludePaths() 为私有装配逻辑，白名单装配无外部副作用，直接反射读取其产物
        Method excludePaths = SaTokenConfig.class.getDeclaredMethod("excludePaths");
        excludePaths.setAccessible(true);
        return (List<String>) excludePaths.invoke(config);
    }
}
