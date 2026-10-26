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

import java.util.Collections;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * N-03：受控目标查询必须补全角色/部门关系；关系缺失时 fail-closed，不能带着不完整上下文做支配判断。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserTargetHydrationTest {

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

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private SysUser user() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_target");
        return user;
    }

    @Test
    @DisplayName("受控查询不可见：统一 403，不泄露目标是否存在")
    void invisibleTargetIsRejected() {
        when(sysUserScopeQuery.findVisibleUser(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getScopedUser(9L));

        assertEquals(403, ex.getCode());
        assertEquals("用户不存在或无权操作", ex.getMessage());
    }

    @Test
    @DisplayName("角色关系缺失：fail-closed 403")
    void missingRoleHydrationFailsClosed() {
        when(sysUserScopeQuery.findVisibleUser(9L)).thenReturn(user());
        when(baseMapper.getById(any())).thenReturn(user());
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserRole(9L, 2L)));
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getScopedUser(9L));

        assertEquals("用户角色关系不完整，拒绝操作", ex.getMessage());
    }

    @Test
    @DisplayName("部门关系缺失：fail-closed 403")
    void missingDeptHydrationFailsClosed() {
        SysUser user = user();
        user.setRoleList(Collections.emptyList());
        when(sysUserScopeQuery.findVisibleUser(9L)).thenReturn(user);
        when(baseMapper.getById(any())).thenReturn(user);
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserDept(9L, 3L)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getScopedUser(9L));

        assertEquals("用户部门关系不完整，拒绝操作", ex.getMessage());
    }

    @Test
    @DisplayName("关系完整：返回补全后的目标用户")
    void completeHydrationReturnsTarget() {
        SysUser user = user();
        SysRole role = new SysRole();
        role.setId(2L);
        SysDept dept = new SysDept();
        dept.setId(3L);
        when(sysUserScopeQuery.findVisibleUser(9L)).thenReturn(user);
        when(baseMapper.getById(any())).thenReturn(user);
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserRole(9L, 2L)));
        when(sysRoleService.listByIds(any())).thenReturn(Collections.singletonList(role));
        when(sysUserDeptMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserDept(9L, 3L)));
        when(sysDeptService.listByIds(any())).thenReturn(Collections.singletonList(dept));

        SysUser result = service.getScopedUser(9L);

        assertNotNull(result);
        assertEquals(1, result.getRoleList().size());
        assertEquals(1, result.getDeptList().size());
    }
}
