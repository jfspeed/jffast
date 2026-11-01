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

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.system.service.SysMenuService;
import cn.jfspeed.system.service.SysSafeService;
import cn.jfspeed.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysLoginController MockMvc 单元测试。
 * <p>
 * 覆盖 needCaptcha（RedisUtil 静态）、logout（LoginUtil 静态）、getUserInfo（LoginUtil 静态 + sysMenuService）。
 * login 端点静态方法过多（RSAUtil/EncryptionUtil/RedisUtil/IdUtils/IPUtil）留给集成测试。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysLoginControllerTest {

    @Mock
    private SysSafeService sysSafeService;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysLogService sysLogService;
    @Mock
    private SysMenuService sysMenuService;
    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @InjectMocks
    private SysLoginController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 控制器走构造器注入（ObjectProvider）后，@Resource 字段不会被 @InjectMocks 自动填充
        ReflectionTestUtils.setField(controller, "sysSafeService", sysSafeService);
        ReflectionTestUtils.setField(controller, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(controller, "sysLogService", sysLogService);
        ReflectionTestUtils.setField(controller, "sysMenuService", sysMenuService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private SysUser buildUser(Long id, String userName) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUserName(userName);
        u.setRealName(userName);
        return u;
    }

    @Nested
    @DisplayName("GET /sys/needCaptcha")
    class NeedCaptchaEndpoint {

        @Test
        @DisplayName("无错误记录应返回 need=false")
        void shouldReturnFalseWhenNoErrorRecord() throws Exception {
            try (MockedStatic<RedisUtil> mocked = mockStatic(RedisUtil.class)) {
                mocked.when(() -> RedisUtil.hasKey(anyString())).thenReturn(false);

                mockMvc.perform(get("/sys/needCaptcha").param("userName", "admin"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data").value(false));
            }
        }

        @Test
        @DisplayName("错误次数 < 2 应返回 need=false")
        void shouldReturnFalseWhenErrorNumLessThanTwo() throws Exception {
            try (MockedStatic<RedisUtil> mocked = mockStatic(RedisUtil.class)) {
                mocked.when(() -> RedisUtil.hasKey(anyString())).thenReturn(true);
                mocked.when(() -> RedisUtil.hget(anyString(), eq("errorNum"))).thenReturn(1);

                mockMvc.perform(get("/sys/needCaptcha").param("userName", "admin"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data").value(false));
            }
        }

        @Test
        @DisplayName("错误次数 >= 2 应返回 need=true")
        void shouldReturnTrueWhenErrorNumGeTwo() throws Exception {
            try (MockedStatic<RedisUtil> mocked = mockStatic(RedisUtil.class)) {
                mocked.when(() -> RedisUtil.hasKey(anyString())).thenReturn(true);
                mocked.when(() -> RedisUtil.hget(anyString(), eq("errorNum"))).thenReturn(2);

                mockMvc.perform(get("/sys/needCaptcha").param("userName", "admin"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data").value(true));
            }
        }
    }

    @Nested
    @DisplayName("DELETE /sys/logout")
    class LogoutEndpoint {

        @Test
        @DisplayName("应调用 LoginUtil.logout 并返回成功")
        void shouldLogoutSuccessfully() throws Exception {
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(() -> LoginUtil.logout(anyString())).thenAnswer(inv -> null);

                mockMvc.perform(delete("/sys/logout").header(BaseConstant.TOKEN_HEADER, "abc-token"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200));

                mocked.verify(() -> LoginUtil.logout("abc-token"));
            }
        }
    }

    @Nested
    @DisplayName("GET /sys/getUserInfo")
    class GetUserInfoEndpoint {

        @Test
        @DisplayName("未登录应返回错误")
        void shouldReturnErrorWhenNotLogin() throws Exception {
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(LoginUtil::getLoginSysUser).thenReturn(null);

                mockMvc.perform(get("/sys/getUserInfo"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(401))
                        .andExpect(jsonPath("$.message").value("请先登录"));
            }
        }

        @Test
        @DisplayName("已登录应返回用户信息与权限")
        void shouldReturnUserInfoWhenLogin() throws Exception {
            SysUser user = buildUser(1L, "admin");
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(LoginUtil::getLoginSysUser).thenReturn(user);
                List<Long> roleIdList = Collections.singletonList(2L);
                mocked.when(LoginUtil::getRoleIdList).thenReturn(roleIdList);
                mocked.when(() -> LoginUtil.getPermList(any())).thenReturn(Arrays.asList("system:user:view"));

                when(sysMenuService.selectMenuListByRoleIds(roleIdList)).thenReturn(new ArrayList<>());
                when(sysMenuService.buildMenus(any())).thenReturn(new ArrayList<>());

                mockMvc.perform(get("/sys/getUserInfo"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data.user.userName").value("admin"))
                        .andExpect(jsonPath("$.data.permissions[0]").value("system:user:view"));

                verify(sysMenuService).selectMenuListByRoleIds(roleIdList);
            }
        }

        @Test
        @DisplayName("无角色应返回空菜单且不按角色查询菜单")
        void shouldReturnEmptyMenusWhenNoRoles() throws Exception {
            SysUser user = buildUser(2L, "norole");
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(LoginUtil::getLoginSysUser).thenReturn(user);
                mocked.when(LoginUtil::getRoleIdList).thenReturn(Collections.emptyList());
                mocked.when(() -> LoginUtil.getPermList(any())).thenReturn(Collections.emptyList());
                when(sysMenuService.buildMenus(any())).thenReturn(new ArrayList<>());

                mockMvc.perform(get("/sys/getUserInfo"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200))
                        .andExpect(jsonPath("$.data.menuRoutes").isEmpty());

                verify(sysMenuService, never()).selectMenuListByRoleIds(any());
                verify(sysMenuService, never()).selectMenuList(any());
            }
        }

        @Test
        @DisplayName("多角色应按全部角色查询菜单并集")
        void shouldQueryMenusByAllRoleIds() throws Exception {
            SysUser user = buildUser(3L, "multi");
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(LoginUtil::getLoginSysUser).thenReturn(user);
                List<Long> roleIdList = Arrays.asList(2L, 3L);
                mocked.when(LoginUtil::getRoleIdList).thenReturn(roleIdList);
                mocked.when(() -> LoginUtil.getPermList(any())).thenReturn(Collections.emptyList());
                when(sysMenuService.selectMenuListByRoleIds(roleIdList)).thenReturn(new ArrayList<>());
                when(sysMenuService.buildMenus(any())).thenReturn(new ArrayList<>());

                mockMvc.perform(get("/sys/getUserInfo"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200));

                verify(sysMenuService).selectMenuListByRoleIds(roleIdList);
            }
        }

        @Test
        @DisplayName("超管应走全量菜单查询而不是角色并集")
        void shouldQueryAllMenusForSuperAdmin() throws Exception {
            SysUser user = buildUser(1L, "admin");
            try (MockedStatic<LoginUtil> mocked = mockStatic(LoginUtil.class)) {
                mocked.when(LoginUtil::getLoginSysUser).thenReturn(user);
                List<Long> roleIdList = Collections.singletonList(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG);
                mocked.when(LoginUtil::getRoleIdList).thenReturn(roleIdList);
                mocked.when(() -> LoginUtil.getPermList(any())).thenReturn(Collections.emptyList());
                when(sysMenuService.selectMenuList(any())).thenReturn(new ArrayList<>());
                when(sysMenuService.buildMenus(any())).thenReturn(new ArrayList<>());

                mockMvc.perform(get("/sys/getUserInfo"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value(200));

                verify(sysMenuService).selectMenuList(any());
                verify(sysMenuService, never()).selectMenuListByRoleIds(any());
            }
        }
    }
}
