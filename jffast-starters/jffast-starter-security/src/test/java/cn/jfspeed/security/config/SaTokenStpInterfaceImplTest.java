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

import cn.jfspeed.common.security.util.LoginUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

/**
 * Sa-Token 权限/角色数据源实现单元测试。
 * <p>
 * 登录态由 {@code LoginUtil} 静态方法提供，无会话时应返回空列表而非 null
 * （Sa-Token 对 null 的兼容行为未定义，空列表是安全兜底）。
 *
 * @author jffast
 */
class SaTokenStpInterfaceImplTest {

    private final SaTokenStpInterfaceImpl stpInterface = new SaTokenStpInterfaceImpl();

    @Test
    @DisplayName("LoginUtil 返回 null 时兜底为空列表")
    void nullFallsBackToEmpty() {
        try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
            mocked.when(LoginUtil::getPermList).thenReturn(null);
            mocked.when(LoginUtil::getRoleCodeList).thenReturn(null);

            assertTrue(stpInterface.getPermissionList(1L, "login").isEmpty());
            assertTrue(stpInterface.getRoleList(1L, "login").isEmpty());
        }
    }

    @Test
    @DisplayName("有登录态时原样透传权限与角色")
    void passesThrough() {
        try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
            mocked.when(LoginUtil::getPermList).thenReturn(List.of("system:user:list"));
            mocked.when(LoginUtil::getRoleCodeList).thenReturn(List.of("admin"));

            assertEquals(List.of("system:user:list"), stpInterface.getPermissionList(1L, "login"));
            assertEquals(List.of("admin"), stpInterface.getRoleList(1L, "login"));
        }
    }
}
