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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysRoleDept;
import cn.jfspeed.system.event.RoleAuthorizationChangedEvent;
import cn.jfspeed.system.mapper.SysRoleMapper;

/**
 * 有效角色定义单次计算（N-09/N-28）、显式 create/update（N-27/N-26）、
 * 菜单/部门 null 语义（N-11/N-23/P3-13）与部门绑定变更判定（N-15）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleEffectiveDataScopeTest {

    private static final int DATA_SCOPE_CUSTOM_DEPT = 5;

    @Mock
    private SysMenuService sysMenuService;

    @Mock
    private SysRoleMenuService sysRoleMenuService;

    @Mock
    private SysRoleDeptService sysRoleDeptService;

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SysRoleService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", sysRoleMapper);
        when(sysRoleMapper.insert(any(SysRole.class))).thenReturn(1);
        when(sysRoleMapper.updateById(any(SysRole.class))).thenReturn(1);
        when(sysRoleDeptService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
    }

    private static SysRoleRequest request(String roleKey, Integer dataScope) {
        SysRoleRequest req = new SysRoleRequest();
        req.setRoleKey(roleKey);
        req.setDataScope(dataScope);
        return req;
    }

    private static SysRole existing(Long id, String roleKey, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setDataScope(dataScope);
        return role;
    }

    private static SysRole updateEntity(Long id, String roleKey, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey(roleKey);
        role.setDataScope(dataScope);
        return role;
    }

    @Test
    @DisplayName("创建：roleKey 缺失且无既有值 -> 拒绝（不按默认映射猜测）")
    void blankRoleKeyRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.resolveEffectiveRole(request("", 3), null));
        assertEquals("角色标识不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("创建：dataScope 缺省按 roleKey 默认映射，仅计算一次")
    void createUsesDefaultMapping() {
        assertEquals(1, service.resolveEffectiveRole(request("super_admin", null), null).dataScope());
        assertEquals(2, service.resolveEffectiveRole(request("admin", null), null).dataScope());
        assertEquals(4, service.resolveEffectiveRole(request("common", null), null).dataScope());
        assertEquals(3, service.resolveEffectiveRole(request("report", null), null).dataScope());
    }

    @Test
    @DisplayName("更新：请求未携带 roleKey/dataScope 时沿用库中原值")
    void updatePreservesExistingDefinition() {
        SysRoleService.EffectiveRole effective =
                service.resolveEffectiveRole(request(null, null), existing(9L, "dept_ops", 4));
        assertEquals("dept_ops", effective.roleKey());
        assertEquals(4, effective.dataScope());
    }

    @Test
    @DisplayName("旧包装 saveRole 两个重载已删除（N-28）")
    void legacySaveRoleRemoved() {
        assertThrows(NoSuchMethodException.class,
                () -> SysRoleService.class.getDeclaredMethod("saveRole", SysRole.class));
        assertThrows(NoSuchMethodException.class,
                () -> SysRoleService.class.getDeclaredMethod("saveRole", SysRole.class, boolean.class));
    }

    @Test
    @DisplayName("updateRole 使用 Controller 写回的实体值，不在服务层重新按 roleKey 计算（P3-12/P3-17）")
    void updateRoleUsesEntityValues() {
        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "dept_ops", 4));

        service.updateRole(updateEntity(9L, "dept_ops_v2", 3), false, false);

        ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
        verify(sysRoleMapper).updateById(captor.capture());
        assertEquals("dept_ops_v2", captor.getValue().getRoleKey(), "落库值必须是 Controller 写回的有效值");
        assertEquals(3, captor.getValue().getDataScope(), "落库值必须是 Controller 写回的有效值");
        verify(eventPublisher).publishEvent(any(RoleAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("updateRole 对 null dataScope fail-closed，不回落到默认映射")
    void updateRoleRejectsNullDataScope() {
        SysRole role = updateEntity(9L, "dept_ops", null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRole(role, false, false));
        assertEquals("角色定义数据不完整", ex.getMessage());
        verify(sysRoleMapper, never()).updateById(any(SysRole.class));
    }

    @Test
    @DisplayName("更新入口：id 为空/目标不存在/更新失败一律角色不存在")
    void updateEntryRejectsInvalidTarget() {
        assertEquals("角色不存在", assertThrows(BusinessException.class,
                () -> service.updateRole(null, false, false)).getMessage());

        SysRole noId = updateEntity(null, "ops", 3);
        assertEquals("角色不存在", assertThrows(BusinessException.class,
                () -> service.updateRole(noId, false, false)).getMessage());

        when(sysRoleMapper.selectById(9L)).thenReturn(null);
        assertEquals("角色不存在", assertThrows(BusinessException.class,
                () -> service.updateRole(updateEntity(9L, "ops", 3), false, false)).getMessage());

        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "ops", 3));
        when(sysRoleMapper.updateById(any(SysRole.class))).thenReturn(0);
        assertEquals("角色不存在", assertThrows(BusinessException.class,
                () -> service.updateRole(updateEntity(9L, "ops", 3), false, false)).getMessage());
    }

    @Test
    @DisplayName("syncMenus=true 且 menuIds=null：保持既有菜单绑定，不清空（P3-13）")
    void syncMenusWithoutPayloadKeepsBindings() {
        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "ops", 3));

        service.updateRole(updateEntity(9L, "ops", 3), true, false);

        verify(sysRoleMenuService, never()).remove(any());
        verify(sysRoleMenuService, never()).insertBatch(any());
        verify(sysRoleMenuService, never()).clearRoleCache(any());
    }

    @Test
    @DisplayName("syncDepts=true 且 deptIds=null：自定义范围内保持绑定，不清空（N-23/N-29）")
    void syncDeptsWithoutPayloadKeepsBindings() {
        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "ops", DATA_SCOPE_CUSTOM_DEPT));

        service.updateRole(updateEntity(9L, "ops", DATA_SCOPE_CUSTOM_DEPT), false, true);

        verify(sysRoleDeptService, never()).remove(any());
        verify(sysRoleDeptService, never()).insertBatch(any());
    }

    @Test
    @DisplayName("insertRoleDept(null) 为保持绑定的 no-op")
    void insertRoleDeptNullIsNoop() {
        service.insertRoleDept(9L, null);
        verify(sysRoleDeptService, never()).remove(any());
        verify(sysRoleDeptService, never()).insertBatch(any());
    }

    @Test
    @DisplayName("insertRoleDept：重复 deptId 去重后只落一条绑定（P3-21）")
    @SuppressWarnings("unchecked")
    void insertRoleDeptDeduplicates() {
        service.insertRoleDept(9L, new Long[]{3L, 3L, null});

        verify(sysRoleDeptService).remove(any());
        ArgumentCaptor<List<SysRoleDept>> captor = ArgumentCaptor.forClass(List.class);
        verify(sysRoleDeptService).insertBatch(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(3L, captor.getValue().get(0).getDeptId());
    }

    @Test
    @DisplayName("createRole：id 为空 fail-closed，不落库（P3-15）")
    void createRoleRejectsNullId() {
        SysRole role = updateEntity(null, "ops", 3);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createRole(role));

        assertEquals("角色 ID 不能为空", ex.getMessage());
        verify(sysRoleMapper, never()).insert(any(SysRole.class));
    }

    @Test
    @DisplayName("自定义范围部门集合变化 -> 发布角色授权变更事件；集合相同 -> 不发布")
    void deptBindingsChangedTriggersEvent() {
        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "ops", DATA_SCOPE_CUSTOM_DEPT));
        SysRoleDept binding = new SysRoleDept();
        binding.setRoleId(9L);
        binding.setDeptId(10L);
        when(sysRoleDeptService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(binding));

        SysRole unchanged = updateEntity(9L, "ops", DATA_SCOPE_CUSTOM_DEPT);
        unchanged.setDeptIds(new Long[]{10L});
        service.updateRole(unchanged, false, true);
        verify(eventPublisher, never()).publishEvent(any());

        SysRole changed = updateEntity(9L, "ops", DATA_SCOPE_CUSTOM_DEPT);
        changed.setDeptIds(new Long[]{11L});
        service.updateRole(changed, false, true);
        verify(eventPublisher).publishEvent(any(RoleAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("isOpen 仅为菜单树展开标记：变更不发布授权事件，但仍落库（N-17）")
    void isOpenChangeDoesNotKick() {
        SysRole db = existing(9L, "ops", 3);
        db.setIsOpen(false);
        when(sysRoleMapper.selectById(9L)).thenReturn(db);

        SysRole update = updateEntity(9L, "ops", 3);
        update.setIsOpen(true);
        service.updateRole(update, false, false);

        verify(sysRoleMapper).updateById(update);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("非自定义范围更新：清理角色-部门绑定")
    void nonCustomScopeClearsDeptBindings() {
        when(sysRoleMapper.selectById(9L)).thenReturn(existing(9L, "ops", 3));

        service.updateRole(updateEntity(9L, "ops", 3), false, false);

        verify(sysRoleDeptService).remove(any());
    }

    @Test
    @DisplayName("创建路径不发布角色授权事件（N-26）；dataScope=5 + deptIds=null 不写部门绑定")
    void createDoesNotPublishOrWriteDeptBindings() {
        SysRole role = updateEntity(9L, "ops", DATA_SCOPE_CUSTOM_DEPT);
        role.setMenuIds(new Long[0]);

        service.createRole(role);

        verify(sysRoleMapper).insert(any(SysRole.class));
        verify(eventPublisher, never()).publishEvent(any());
        verify(sysRoleDeptService, never()).insertBatch(any());
        verify(sysRoleMenuService).remove(any());
    }
}
