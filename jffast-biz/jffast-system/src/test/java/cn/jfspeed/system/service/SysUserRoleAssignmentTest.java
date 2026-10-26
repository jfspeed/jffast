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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R-02 / N-13：用户角色/部门集合的 null=不修改、[]=清空、非空=替换；
 * N-20：非空 deptId 必须存在。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserRoleAssignmentTest {

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
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

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
        SysDept dept = new SysDept();
        dept.setId(10L);
        dept.setDeptName("技术部");
        when(sysDeptService.getById(10L)).thenReturn(dept);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
    }

    private SysUser existingUser() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_assign_u");
        user.setDeptId(10L);
        return user;
    }

    private SysUser requestUser() {
        SysUser user = existingUser();
        when(baseMapper.selectById(9L)).thenReturn(existingUser());
        return user;
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(3);
        return role;
    }

    private SysDept dept(Long id) {
        SysDept dept = new SysDept();
        dept.setId(id);
        return dept;
    }

    @Test
    @DisplayName("roleList=null：不触碰 sys_user_role，也不写 role_id")
    void nullRoleListKeepsExistingBinding() {
        SysUser user = requestUser();

        service.saveUser(user);

        verify(sysUserRoleMapper, never()).delete(any());
        verify(sysUserRoleMapper, never()).insertBatch(anyList());
        assertNull(user.getRoleId());
    }

    @Test
    @DisplayName("roleList=[]：清空关系表并显式写空串缓存")
    void emptyRoleListClearsBinding() {
        SysUser user = requestUser();
        user.setRoleList(Collections.emptyList());
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserRole(9L, 2L)));
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper, never()).insertBatch(anyList());
        assertEquals("", user.getRoleId());
    }

    @Test
    @DisplayName("非空 roleList：替换关系表")
    void nonEmptyRoleListReplacesBinding() {
        SysUser user = requestUser();
        List<SysRole> roles = Arrays.asList(role(2L), role(3L));
        user.setRoleList(roles);
        when(sysRoleService.listByIds(any())).thenReturn(roles);
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserRoleMapper).delete(any());
        verify(sysUserRoleMapper).insertBatch(anyList());
        assertEquals("2,3", user.getRoleId());
    }

    @Test
    @DisplayName("deptList=null：不触碰 sys_user_dept")
    void nullDeptListKeepsExistingBinding() {
        SysUser user = requestUser();

        service.saveUser(user);

        verify(sysUserDeptMapper, never()).delete(any());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("deptList=[] 且当前绑定为空：空到空放行，不写绑定")
    void emptyDeptListOnEmptyBindingIsNoOp() {
        SysUser user = requestUser();
        user.setDeptList(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserDeptMapper).delete(any());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("deptList=[] 且当前绑定非空：拒绝清空（INV-1）")
    void emptyDeptListOnNonEmptyBindingIsRejected() {
        SysUser user = requestUser();
        user.setDeptList(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserDept(9L, 10L)));
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(user));

        assertEquals(403, ex.getCode());
        assertEquals("部门绑定不能为空", ex.getMessage());
        verify(sysUserDeptMapper, never()).delete(any());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
        verify(baseMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("非空 deptList：替换 sys_user_dept（含归属部门）")
    void nonEmptyDeptListReplacesBinding() {
        SysUser user = requestUser();
        user.setDeptList(Arrays.asList(dept(3L), dept(4L), dept(10L)));
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserDeptMapper).delete(any());
        verify(sysUserDeptMapper).insertBatch(anyList());
    }

    @Test
    @DisplayName("deptId 指向不存在的部门（含 0）必须拒绝，且不写库")
    void rejectsMissingDeptId() {
        SysUser user = existingUser();
        user.setDeptId(0L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(user));

        assertEquals("部门不存在", ex.getMessage());
        verify(baseMapper, never()).insert(any(SysUser.class));
        verify(baseMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("超管即使携带 roleList 也不改关系表/缓存")
    void superAdminRoleListIsIgnored() {
        SysUser user = requestUser();
        user.setUserName(BaseConstant.SUPER_ADMIN);
        user.setRoleList(Collections.singletonList(role(2L)));
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserRoleMapper, never()).delete(any());
        verify(baseMapper, never()).update(isNull(), any());
        assertTrue(user.getRoleId() == null);
    }
}
