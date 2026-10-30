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

import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserPost;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserPostMapper;
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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * N-13 / N-25 读取侧：{@code role_id = NULL} 与 {@code ''} 都等价于「空角色集合」，
 * 关系表为空时不得伪造出角色/部门/岗位。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserFieldSemanticsTest {

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserPostMapper sysUserPostMapper;
    @Mock
    private SysPostService sysPostService;
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

    @Test
    @DisplayName("关系表为空（role_id 为 NULL 或 ''）读取结果均为空角色/部门/岗位")
    void emptyRelationsYieldEmptyCollections() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_semantics");
        user.setRoleId(null);
        when(baseMapper.getById(any())).thenReturn(user);
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserPostMapper.selectList(any())).thenReturn(Collections.emptyList());

        SysUser result = service.getUser(9L, null, null);

        assertNull(result.getRoleList(), "无关系行时不得凭空生成角色集合");
        assertNull(result.getDeptList(), "无关系行时不得凭空生成部门集合");
        assertNull(result.getPostList(), "无关系行时不得凭空生成岗位集合");
    }

    @Test
    @DisplayName("关系行存在但角色/岗位已被删除：返回空集合而不是 null")
    void danglingRelationYieldsEmptyList() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_semantics2");
        when(baseMapper.getById(any())).thenReturn(user);
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserRole(9L, 999L)));
        when(sysRoleService.listByIds(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserPostMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserPost(9L, 888L)));
        when(sysPostService.listByIds(any())).thenReturn(Collections.emptyList());

        SysUser result = service.getUser(9L, null, null);

        org.junit.jupiter.api.Assertions.assertNotNull(result.getRoleList());
        org.junit.jupiter.api.Assertions.assertTrue(result.getRoleList().isEmpty(),
                "悬空关系应表现为空集合");
        org.junit.jupiter.api.Assertions.assertNotNull(result.getPostList());
        org.junit.jupiter.api.Assertions.assertTrue(result.getPostList().isEmpty(),
                "悬空岗位绑定应表现为空集合");
    }
}
