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

import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.event.UserAuthorizationChangedEvent;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N-04 / N-10：用户角色/部门/deptId 变化才发布事件；集合等价与顺序无关。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserSessionInvalidationTest {

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
        when(sysDeptService.getById(any())).thenReturn(dept);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(sysRoleService.listByIds(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private SysUser existing(Long deptId) {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_session");
        user.setDeptId(deptId);
        when(baseMapper.selectById(9L)).thenReturn(user);
        return user;
    }

    private SysUser request(Long deptId) {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_session");
        user.setDeptId(deptId);
        return user;
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(3);
        return role;
    }

    @Test
    @DisplayName("角色集合顺序不同视为未变化：不发布事件")
    void sameRoleSetDifferentOrderDoesNotPublish() {
        existing(10L);
        SysUser request = request(10L);
        request.setRoleList(Arrays.asList(role(2L), role(3L)));
        when(sysUserRoleMapper.selectList(any()))
                .thenReturn(Arrays.asList(new SysUserRole(9L, 3L), new SysUserRole(9L, 2L)));
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(request);

        verify(eventPublisher, never()).publishEvent(any(UserAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("角色集合变化：发布用户授权变更事件")
    void changedRoleSetPublishes() {
        existing(10L);
        SysUser request = request(10L);
        request.setRoleList(Collections.singletonList(role(2L)));
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(request);

        verify(eventPublisher).publishEvent(any(UserAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("部门绑定变化：发布事件")
    void changedDeptSetPublishes() {
        existing(10L);
        SysUser request = request(10L);
        SysDept dept = new SysDept();
        dept.setId(3L);
        SysDept ownerDept = new SysDept();
        ownerDept.setId(10L);
        request.setDeptList(Arrays.asList(dept, ownerDept));
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(request);

        verify(eventPublisher).publishEvent(any(UserAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("deptId 变化：带含新归属的绑定集合时发布事件")
    void changedDeptIdPublishes() {
        existing(10L);
        SysDept dept = new SysDept();
        dept.setId(11L);
        when(sysDeptService.getById(11L)).thenReturn(dept);
        SysUser request = request(11L);
        request.setDeptList(Collections.singletonList(dept));
        when(sysUserDeptMapper.selectList(any()))
                .thenReturn(Collections.singletonList(new SysUserDept(9L, 10L)));

        service.saveUser(request);

        verify(eventPublisher).publishEvent(any(UserAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("仅普通字段变化：不发布事件")
    void plainFieldChangeDoesNotPublish() {
        existing(10L);
        SysUser request = request(10L);
        request.setRealName("新姓名");

        service.saveUser(request);

        verify(eventPublisher, never()).publishEvent(any(UserAuthorizationChangedEvent.class));
    }

    @Test
    @DisplayName("创建用户：不发布授权变更事件")
    void createDoesNotPublish() {
        SysUser request = request(10L);
        request.setId(null);
        request.setRoleList(Collections.singletonList(role(2L)));
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.saveUser(request);

        verify(eventPublisher, never()).publishEvent(any(UserAuthorizationChangedEvent.class));
        verify(sysUserRoleMapper).insertBatch(anyList());
    }
}
