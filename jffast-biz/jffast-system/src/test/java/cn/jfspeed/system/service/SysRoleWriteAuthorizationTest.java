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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

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
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.mapper.SysRoleMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * 角色定义写侧授权（R-02/N-02/N-09/N-18）与删除保护（R-02）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleWriteAuthorizationTest {

    @Mock
    private SysMenuService sysMenuService;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysRoleMenuService sysRoleMenuService;

    @Mock
    private SysRoleDeptService sysRoleDeptService;

    @InjectMocks
    private SysRoleService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", sysRoleMapper);
        when(sysMenuService.getById(any())).thenReturn(null);
        when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
    }

    private static SysRoleService.OperatorContext operator(long id, int rank, boolean superAdmin, Long... menuIds) {
        return new SysRoleService.OperatorContext(id, List.of(2L), rank,
                Set.of(menuIds), superAdmin);
    }

    private static SysRoleRequest request(String roleKey, Integer dataScope, Long... menuIds) {
        SysRoleRequest req = new SysRoleRequest();
        req.setRoleKey(roleKey);
        req.setDataScope(dataScope);
        if (menuIds != null) {
            req.setMenuIds(menuIds);
        }
        return req;
    }

    private static SysRole existing(Long id, String roleKey, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setDataScope(dataScope);
        return role;
    }

    private BusinessException reject(SysRoleRequest req, SysRole existing,
                                     SysRoleService.EffectiveRole effective,
                                     SysRoleService.OperatorContext operator) {
        return assertThrows(BusinessException.class,
                () -> service.validateRoleWrite(req, existing, effective, operator));
    }

    @Test
    @DisplayName("超管写角色定义：不受数据范围/秩/菜单子集规则限制")
    void superAdminBypassesRules() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("report_admin", 1);
        service.validateRoleWrite(request("report_admin", 1, 999L), null, effective,
                operator(100L, Integer.MAX_VALUE, true));
    }

    @Test
    @DisplayName("非超管不得设置全部数据范围（1）")
    void nonSuperCannotSetAllScope() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 1);
        BusinessException ex = reject(request("ops", 1), null, effective, operator(100L, 2, false));
        assertEquals(403, ex.getCode());
        assertEquals("无权设置该数据范围", ex.getMessage());
    }

    @Test
    @DisplayName("非超管不得设置自定义部门范围（5）")
    void nonSuperCannotSetCustomDeptScope() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 5);
        BusinessException ex = reject(request("ops", 5), null, effective, operator(100L, 2, false));
        assertEquals("无权设置该数据范围", ex.getMessage());
    }

    @Test
    @DisplayName("操作者秩为 0：fail-closed 拒绝，不退化为放行")
    void rankZeroFailsClosed() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 4);
        BusinessException ex = reject(request("ops", 4), null, effective, operator(100L, 0, false));
        assertEquals("操作者数据范围异常", ex.getMessage());
    }

    @Test
    @DisplayName("非超管不得设置高于自己的数据范围")
    void rankAboveOperatorRejected() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 2);
        BusinessException ex = reject(request("ops", 2), null, effective, operator(100L, 1, false));
        assertEquals("无权设置高于自己的数据范围", ex.getMessage());
    }

    @Test
    @DisplayName("非超管不得修改自己所属的角色")
    void selfRoleRejected() {
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        SysRole target = existing(7L, "dept_ops", 4);
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("dept_ops", 4);
        BusinessException ex = reject(request("dept_ops", 4), target, effective, operator(100L, 2, false));
        assertEquals("不能修改自己所属的角色", ex.getMessage());
    }

    @Test
    @DisplayName("内置角色标识不可改名")
    void builtInRoleKeyRenameRejected() {
        SysRole target = existing(9L, "admin", 2);
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("admin_v2", 2);
        BusinessException ex = reject(request("admin_v2", 2), target, effective, operator(100L, 2, false));
        assertEquals("内置角色标识不允许修改", ex.getMessage());
    }

    @Test
    @DisplayName("非超管创建角色不得使用内置 roleKey")
    void nonSuperCannotUseBuiltInRoleKey() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("admin", 2);
        BusinessException ex = reject(request("admin", 2), null, effective, operator(100L, 2, false));
        assertEquals("无权使用内置角色标识", ex.getMessage());
    }

    @Test
    @DisplayName("内置超管角色仅超管可改，且标识/范围不可变")
    void superAdminRoleIdProtected() {
        SysRole target = existing(1L, "super_admin", 1);
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("super_admin", 1);
        BusinessException ex = reject(request("super_admin", 1), target, effective, operator(100L, 2, false));
        assertEquals("内置超管角色不允许删除/修改标识", ex.getMessage());
    }

    @Test
    @DisplayName("请求菜单（含祖先）必须是操作者已有菜单的子集")
    void menuOutsideOperatorRejected() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 3);
        BusinessException ex = reject(request("ops", 3, 9L), null, effective, operator(100L, 2, false, 5L));
        assertEquals("无权分配未拥有的菜单或按钮", ex.getMessage());
    }

    @Test
    @DisplayName("请求菜单是操作者菜单子集：放行")
    void menuSubsetPasses() {
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("ops", 3);
        service.validateRoleWrite(request("ops", 3, 5L), null, effective,
                operator(100L, 2, false, 5L, 6L));
    }

    @Test
    @DisplayName("非超管不得操作 dataScope=1 的既有角色")
    void nonSuperCannotOperateAllScopeRole() {
        SysRole target = existing(9L, "global_ops", 1);
        SysRoleService.EffectiveRole effective = new SysRoleService.EffectiveRole("global_ops", 2);
        BusinessException ex = reject(request("global_ops", 2), target, effective, operator(100L, 2, false));
        assertEquals("无权操作该角色", ex.getMessage());
    }

    @Test
    @DisplayName("有效定义缺失：服务层 fail-closed")
    void incompleteEffectiveRejected() {
        assertThrows(BusinessException.class,
                () -> service.validateRoleWrite(request("ops", 3), null, null,
                        operator(100L, 2, false)));
        assertThrows(BusinessException.class,
                () -> service.validateRoleWrite(request("", 3), null,
                        new SysRoleService.EffectiveRole("", 3), operator(100L, 2, false)));
    }

    @Test
    @DisplayName("批量删除：空数组、缺失角色、内置超管角色分别拒绝")
    void deletePreconditions() {
        assertEquals("请选择要删除的角色", service.deleteRoles(new Long[0], operator(100L, 2, true)));
        when(sysRoleMapper.selectById(8L)).thenReturn(null);
        assertEquals("角色不存在", service.deleteRoles(new Long[]{8L}, operator(100L, 2, true)));
        when(sysRoleMapper.selectById(1L)).thenReturn(existing(1L, "super_admin", 1));
        assertEquals("超级管理员角色禁止删除", service.deleteRoles(new Long[]{1L}, operator(100L, 2, true)));
    }

    @Test
    @DisplayName("批量删除：非超管不得删除自己所属/全部数据范围角色")
    void deleteProtectedForNonSuperAdmin() {
        when(sysRoleMapper.selectById(7L)).thenReturn(existing(7L, "dept_ops", 4));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        assertEquals("不能修改自己所属的角色", service.deleteRoles(new Long[]{7L}, operator(100L, 2, false)));

        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "global_ops", 1));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);
        assertEquals("无权操作该角色", service.deleteRoles(new Long[]{9L}, operator(100L, 2, false)));
    }

    @Test
    @DisplayName("批量删除：双表任一存在用户绑定即拒绝")
    void deleteRejectedWhenBound() {
        when(sysRoleMapper.selectById(7L)).thenReturn(existing(7L, "report", 4));
        when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);
        assertEquals("角色已分配用户，不能删除",
                service.deleteRoles(new Long[]{7L}, operator(100L, 2, true)));

        when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.selectCount(any())).thenReturn(1L);
        assertEquals("角色已分配用户，不能删除",
                service.deleteRoles(new Long[]{7L}, operator(100L, 2, false)));
    }
}
