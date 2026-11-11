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
package cn.jfspeed.system.security;

import cn.jfspeed.common.security.spi.LoginDataSource;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysMenuService;
import cn.jfspeed.system.service.SysRoleMenuService;
import cn.jfspeed.system.service.SysUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LoginDataSourceImpl} 的 SPI 方法映射测试：钉住每个 SPI 方法与 system
 * 三服务方法的一一对应关系（R-11 SPI 化取代 LoginUtil 的跨层反射调用后，
 * 映射错位会导致权限数据源取错方法且编译期不可见——本测试兜底）。
 *
 * @author jffast
 */
class LoginDataSourceImplTest {

    private final SysRoleMenuService sysRoleMenuService = mock(SysRoleMenuService.class);
    private final SysMenuService sysMenuService = mock(SysMenuService.class);
    private final SysUserService sysUserService = mock(SysUserService.class);

    private final LoginDataSourceImpl impl =
            new LoginDataSourceImpl(sysRoleMenuService, sysMenuService, sysUserService);

    @Test
    @DisplayName("实现实现 LoginDataSource SPI 接口")
    void implementsSpiInterface() {
        assertTrue(impl instanceof LoginDataSource);
    }

    @Test
    @DisplayName("listAllMenuIdList 委派 SysRoleMenuService.listAllMenuIdList(Long)")
    void listAllMenuIdListDelegates() {
        when(sysRoleMenuService.listAllMenuIdList(5L)).thenReturn(List.of(11L, 12L));

        List<Long> result = impl.listAllMenuIdList(5L);

        assertEquals(List.of(11L, 12L), result);
        verify(sysRoleMenuService).listAllMenuIdList(5L);
    }

    @Test
    @DisplayName("listAllBtnPerm() 无参委派 SysMenuService.listAllBtnPerm()（超管权限全集）")
    void superAdminPermDelegatesToMenuService() {
        when(sysMenuService.listAllBtnPerm()).thenReturn(List.of("sys:user:list"));

        List<String> result = impl.listAllBtnPerm();

        assertEquals(List.of("sys:user:list"), result);
        verify(sysMenuService).listAllBtnPerm();
    }

    @Test
    @DisplayName("listAllBtnPerm(roleId) 委派 SysRoleMenuService.listAllBtnPerm(Long)")
    void rolePermDelegatesToRoleMenuService() {
        when(sysRoleMenuService.listAllBtnPerm(7L)).thenReturn(List.of("sys:role:list"));

        List<String> result = impl.listAllBtnPerm(7L);

        assertEquals(List.of("sys:role:list"), result);
        verify(sysRoleMenuService).listAllBtnPerm(7L);
    }

    @Test
    @DisplayName("getFreshUser 委派 SysUserService.getById(Serializable)，查无此人返回 null")
    void getFreshUserDelegatesToUserService() {
        SysUser fresh = new SysUser();
        fresh.setId(42L);
        when(sysUserService.getById(42L)).thenReturn(fresh);
        when(sysUserService.getById(999L)).thenReturn(null);

        assertSame(fresh, impl.getFreshUser(42L));
        assertNull(impl.getFreshUser(999L));
        verify(sysUserService).getById(42L);
        verify(sysUserService).getById(999L);
    }
}
