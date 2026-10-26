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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全模块配置属性单元测试（默认值 = 不放行任何匿名路径、不注册任何 JSON 类型）。
 *
 * @author jffast
 */
class SecurityPropertiesTest {

    @Test
    @DisplayName("默认两个名单均为空列表（fail-closed）")
    void defaultsAreEmpty() {
        SecurityProperties props = new SecurityProperties();

        assertTrue(props.getAuthExcludes().isEmpty());
        assertTrue(props.getSaJsonAllowTypes().isEmpty());
    }

    @Test
    @DisplayName("可配置免登录路径与 session JSON 类型白名单")
    void configuresLists() {
        SecurityProperties props = new SecurityProperties();
        props.setAuthExcludes(List.of("/oauth2/client/authorize/**"));
        props.setSaJsonAllowTypes(List.of("cn.jfspeed.system.domain.SysUser"));

        assertEquals(List.of("/oauth2/client/authorize/**"), props.getAuthExcludes());
        assertEquals(List.of("cn.jfspeed.system.domain.SysUser"), props.getSaJsonAllowTypes());
    }
}
