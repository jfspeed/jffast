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
package cn.jfspeed.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.mapper.SysRoleMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * 平台级菜单（租户管理）授权守卫：非超管角色保存授权时必须明确报错，不得静默丢弃。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleMenuGrantGuardTest {

    /** 与 SysRoleService#SUPER_ADMIN_ONLY_MENU_IDS 对齐：5014 页面 + 5174~5181 按钮位 */
    private static final long TENANT_MANAGE_MENU_ID = 5014L;
    private static final long TENANT_QUERY_BTN_ID = 5174L;
    private static final long TENANT_ADD_BTN_ID = 5175L;

    @Mock
    private SysMenuService sysMenuService;
    @Mock
    private SysRoleMenuService sysRoleMenuService;
    @Mock
    private SysRoleDeptService sysRoleDeptService;
    @Mock
    private SysRoleMapper sysRoleMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @InjectMocks
    private SysRoleService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", sysRoleMapper);
        when(sysMenuService.getById(any())).thenReturn(null);
    }

    private static SysRole role(long id, String roleKey) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        return role;
    }

    private static SysMenu menu(long id, String name) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setName(name);
        return menu;
    }

    private void givenRole(SysRole role) {
        when(sysRoleMapper.selectById(any())).thenReturn(role);
    }

    @Test
    @DisplayName("非超管只授父菜单（未授子菜单）→ 403 拒绝")
    void rejectsParentMenuForNonSuperAdmin() {
        givenRole(role(2L, "common"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.insertRoleMenu(2L, new Long[]{TENANT_MANAGE_MENU_ID}));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("平台级菜单"));
        // 拒绝发生在删除之前：原有绑定不能被清掉
        verify(sysRoleMenuService, never()).remove(any());
        verify(sysRoleMenuService, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("非超管只授某个子按钮 → 403 拒绝，文案带命中菜单名")
    void rejectsChildButtonForNonSuperAdmin() {
        givenRole(role(2L, "common"));
        when(sysMenuService.getById(TENANT_ADD_BTN_ID)).thenReturn(menu(TENANT_ADD_BTN_ID, "租户新增"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.insertRoleMenu(2L, new Long[]{TENANT_ADD_BTN_ID}));

        assertEquals(403, ex.getCode());
        assertTrue(ex.getMessage().contains("租户新增"));
        verify(sysRoleMenuService, never()).remove(any());
    }

    @Test
    @DisplayName("非超管一次授整棵子树 → 403 拒绝")
    void rejectsWholeSubtreeForNonSuperAdmin() {
        givenRole(role(2L, "common"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.insertRoleMenu(2L,
                        new Long[]{TENANT_MANAGE_MENU_ID, TENANT_QUERY_BTN_ID, TENANT_ADD_BTN_ID}));

        assertEquals(403, ex.getCode());
        verify(sysRoleMenuService, never()).remove(any());
    }

    @Test
    @DisplayName("role_id=1 的超管可照常授权")
    void allowsSuperAdminByRoleId() {
        givenRole(role(1L, "common"));

        service.insertRoleMenu(1L, new Long[]{TENANT_MANAGE_MENU_ID});

        verify(sysRoleMenuService).insertBatch(anyList());
    }

    @Test
    @DisplayName("role_key=super_admin 的超管（id 非 1）可照常授权")
    void allowsSuperAdminByRoleKey() {
        givenRole(role(88L, "super_admin"));

        service.insertRoleMenu(88L, new Long[]{TENANT_MANAGE_MENU_ID});

        verify(sysRoleMenuService).insertBatch(anyList());
    }

    @Test
    @DisplayName("非超管授无关菜单不受影响")
    void allowsUnrelatedMenusForNonSuperAdmin() {
        givenRole(role(2L, "common"));

        service.insertRoleMenu(2L, new Long[]{5000L, 5013L});

        verify(sysRoleMenuService).insertBatch(anyList());
    }

    @Test
    @DisplayName("角色查不到但授平台级菜单 → fail-closed 拒绝")
    void rejectsWhenRoleNotFound() {
        givenRole(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.insertRoleMenu(999L, new Long[]{TENANT_MANAGE_MENU_ID}));

        assertEquals(403, ex.getCode());
    }

    @Test
    @DisplayName("清空授权（删除角色路径）不触发守卫")
    void allowsEmptyMenuIds() {
        givenRole(null);

        service.insertRoleMenu(999L, new Long[0]);

        verify(sysRoleMenuService).remove(any());
        verify(sysRoleMenuService, never()).insertBatch(anyList());
    }
}
