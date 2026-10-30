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
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserPost;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.event.UserAuthorizationChangedEvent;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserPostMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GAP-1：用户↔岗位绑定的三态契约（null=不修改、[]=清空、非空=整组替换）。
 *
 * <p>这里守住的是 R-A / R-B / R-C 三条「全部门禁绿灯仍然错」的静默故障：同步段一旦不受
 * 「键是否存在」控制，任何局部更新都会重算甚至清空绑定，而编译、单测、前端四道门禁全绿。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserPostBindingTest {

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysPostService sysPostService;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserPostMapper sysUserPostMapper;
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
        TableInfoHelper.initTableInfo(assistant, SysUserPost.class);
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        SysDept dept = new SysDept();
        dept.setId(10L);
        dept.setDeptName("技术部");
        when(sysDeptService.getById(10L)).thenReturn(dept);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(baseMapper.selectById(9L)).thenReturn(existingUser());
    }

    private SysUser existingUser() {
        SysUser user = new SysUser();
        user.setId(9L);
        user.setUserName("probe_post_u");
        user.setDeptId(10L);
        return user;
    }

    private SysPost post(Long id) {
        SysPost post = new SysPost();
        post.setId(id);
        post.setPostName("岗位" + id);
        return post;
    }

    private SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(3);
        return role;
    }

    @Test
    @DisplayName("postList=null：完全不触碰 sys_user_post")
    void nullPostListKeepsExistingBinding() {
        SysUser user = existingUser();

        service.saveUser(user);

        verify(sysUserPostMapper, never()).delete(any());
        verify(sysUserPostMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("postList=[]：显式清空该用户全部岗位绑定，只删不插")
    void emptyPostListClearsBinding() {
        SysUser user = existingUser();
        user.setPostList(Collections.emptyList());

        service.saveUser(user);

        verify(sysUserPostMapper).delete(any());
        verify(sysUserPostMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("非空 postList：先删后插整组替换，且顺序无关、去重、忽略 id=null")
    void nonEmptyPostListReplacesBinding() {
        SysUser user = existingUser();
        SysPost nullId = new SysPost();
        user.setPostList(Arrays.asList(post(3L), post(2L), post(3L), nullId, null));
        when(sysPostService.listByIds(any())).thenReturn(List.of(post(2L), post(3L)));

        service.saveUser(user);

        verify(sysUserPostMapper).delete(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysUserPost>> captor = ArgumentCaptor.forClass(List.class);
        verify(sysUserPostMapper).insertBatch(captor.capture());
        assertEquals(List.of(3L, 2L), captor.getValue().stream().map(SysUserPost::getPostId).toList());
        assertEquals(List.of(9L, 9L), captor.getValue().stream().map(SysUserPost::getUserId).toList());
    }

    @Test
    @DisplayName("提交不存在的岗位 id：拒绝且不写关系表")
    void rejectsMissingPostId() {
        SysUser user = existingUser();
        user.setPostList(List.of(post(2L), post(404L)));
        when(sysPostService.listByIds(any())).thenReturn(Collections.singletonList(post(2L)));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(user));

        assertEquals("岗位不存在或已被删除", ex.getMessage());
        verify(sysUserPostMapper, never()).delete(any());
        verify(sysUserPostMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("内置超管：岗位照改，角色照跳过（R-C 负向对照）")
    void superAdminPostsAreStillSynced() {
        SysUser user = existingUser();
        user.setUserName(BaseConstant.SUPER_ADMIN);
        user.setRoleList(Collections.singletonList(role(2L)));
        user.setPostList(Collections.singletonList(post(5L)));
        when(sysPostService.listByIds(any())).thenReturn(Collections.singletonList(post(5L)));

        service.saveUser(user);

        verify(sysUserPostMapper).delete(any());
        verify(sysUserPostMapper).insertBatch(anyList());
        verify(sysUserRoleMapper, never()).delete(any());
        verify(sysUserRoleMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("改岗位不发布授权变更事件（岗位不参与授权，不踢会话）")
    void postChangeDoesNotPublishAuthorizationEvent() {
        SysUser user = existingUser();
        user.setPostList(Collections.singletonList(post(5L)));
        when(sysPostService.listByIds(any())).thenReturn(Collections.singletonList(post(5L)));

        service.saveUser(user);

        verify(eventPublisher, never()).publishEvent(any(UserAuthorizationChangedEvent.class));
    }
}
