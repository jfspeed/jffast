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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * 服务端部门绑定不变量（INV-1/2/3）：拒绝必须发生在写库前，错误码 403。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserDeptBindingInvariantTest {

    private static final Long USER_ID = 9L;
    private static final Long DEPT_ID = 10L;
    private static final Long OTHER_DEPT_ID = 11L;

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserScopeQuery sysUserScopeQuery;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(sysDeptService.getById(any())).thenAnswer(invocation -> dept(invocation.getArgument(0)));
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(baseMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser inserted = invocation.getArgument(0);
            inserted.setId(USER_ID);
            return 1;
        });
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    private void existingUser(Long deptId) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUserName("probe_inv");
        user.setDeptId(deptId);
        when(baseMapper.selectById(USER_ID)).thenReturn(user);
    }

    private static SysUser updateRequest(Long deptId) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUserName("probe_inv");
        user.setDeptId(deptId);
        return user;
    }

    private static SysDept dept(Long id) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setDeptName("部门" + id);
        return dept;
    }

    private static SysUserDept binding(Long deptId) {
        return new SysUserDept(USER_ID, deptId);
    }

    @Test
    @DisplayName("INV-1：更新把非空绑定提交为空 → 403，且不写库")
    void clearNonEmptyBindingIsRejected() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(3L)));
        SysUser request = updateRequest(DEPT_ID);
        request.setDeptList(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(request));

        assertEquals(403, ex.getCode());
        assertEquals("部门绑定不能为空", ex.getMessage());
        verify(baseMapper, never()).updateById(any(SysUser.class));
        verify(sysUserDeptMapper, never()).delete(any());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("INV-1：当前绑定为空时空到空放行")
    void clearEmptyBindingIsAllowed() {
        existingUser(DEPT_ID);
        SysUser request = updateRequest(DEPT_ID);
        request.setDeptList(Collections.emptyList());

        service.saveUser(request);

        verify(baseMapper).updateById(any(SysUser.class));
        verify(sysUserDeptMapper).delete(any());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("INV-1 先于 INV-2：同时清空绑定并改 deptId 仍报「部门绑定不能为空」")
    void clearBindingWinsOverOwnerDeptChange() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(DEPT_ID)));
        SysUser request = updateRequest(OTHER_DEPT_ID);
        request.setDeptList(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(request));

        assertEquals(403, ex.getCode());
        assertEquals("部门绑定不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("INV-2：替换绑定时不含原归属部门 → 403")
    void replaceBindingWithoutOwnerDeptIsRejected() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(3L)));
        SysUser request = updateRequest(DEPT_ID);
        request.setDeptList(List.of(dept(4L)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(request));

        assertEquals(403, ex.getCode());
        assertEquals("归属部门须包含在部门绑定中", ex.getMessage());
        verify(baseMapper, never()).updateById(any(SysUser.class));
        verify(sysUserDeptMapper, never()).delete(any());
    }

    @Test
    @DisplayName("INV-2：仅改 deptId 未带绑定集合 → 403（直连更新同样受控）")
    void changeOwnerDeptWithoutBindingIsRejected() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(DEPT_ID)));
        SysUser request = updateRequest(OTHER_DEPT_ID);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(request));

        assertEquals(403, ex.getCode());
        assertEquals("归属部门须包含在部门绑定中", ex.getMessage());
        verify(baseMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("INV-2：改 deptId 且绑定集合包含新归属 → 放行")
    void changeOwnerDeptWithMatchingBindingIsAllowed() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(DEPT_ID)));
        SysUser request = updateRequest(OTHER_DEPT_ID);
        request.setDeptList(List.of(dept(OTHER_DEPT_ID)));

        service.saveUser(request);

        verify(baseMapper).updateById(any(SysUser.class));
        verify(sysUserDeptMapper).delete(any());
        verify(sysUserDeptMapper).insertBatch(anyList());
    }

    @Test
    @DisplayName("INV-2：仅改 deptId、当前绑定含新值 → 放行")
    void changeOwnerDeptToExistingBindingIsAllowed() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(OTHER_DEPT_ID)));
        SysUser request = updateRequest(OTHER_DEPT_ID);

        service.saveUser(request);

        verify(baseMapper).updateById(any(SysUser.class));
        verify(sysUserDeptMapper, never()).delete(any());
    }

    @Test
    @DisplayName("存量零绑定账号：不改 deptId、不提交 deptList 的普通编辑仍放行")
    void legacyZeroBindingPlainEditIsAllowed() {
        existingUser(DEPT_ID);
        SysUser request = updateRequest(DEPT_ID);
        request.setRealName("新姓名");

        service.saveUser(request);

        verify(baseMapper).updateById(any(SysUser.class));
        verify(sysUserDeptMapper, never()).delete(any());
    }

    @Test
    @DisplayName("deptId 与绑定集合都未变化：不触发不变量，按提交集合重写")
    void unchangedOwnerDeptAndBindingsDoNotTriggerInvariants() {
        existingUser(DEPT_ID);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(binding(DEPT_ID)));
        SysUser request = updateRequest(DEPT_ID);
        request.setDeptList(List.of(dept(DEPT_ID)));

        service.saveUser(request);

        verify(baseMapper).updateById(any(SysUser.class));
        verify(sysUserDeptMapper).delete(any());
        verify(sysUserDeptMapper).insertBatch(anyList());
    }

    @Test
    @DisplayName("INV-3：创建时显式绑定不含 deptId → 403，且不插入用户")
    void createWithExplicitBindingMissingOwnerDeptIsRejected() {
        SysUser request = new SysUser();
        request.setUserName("probe_inv_create");
        request.setDeptId(DEPT_ID);
        request.setDeptList(List.of(dept(5L)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(request));

        assertEquals(403, ex.getCode());
        assertEquals("归属部门须包含在部门绑定中", ex.getMessage());
        verify(baseMapper, never()).insert(any(SysUser.class));
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("INV-3：创建时显式绑定包含 deptId → 放行")
    void createWithExplicitBindingContainingOwnerDeptIsAllowed() {
        SysUser request = new SysUser();
        request.setUserName("probe_inv_create_ok");
        request.setDeptId(DEPT_ID);
        request.setDeptList(List.of(dept(5L), dept(DEPT_ID)));

        service.saveUser(request);

        verify(baseMapper).insert(any(SysUser.class));
        verify(sysUserDeptMapper).insertBatch(anyList());
    }
}
