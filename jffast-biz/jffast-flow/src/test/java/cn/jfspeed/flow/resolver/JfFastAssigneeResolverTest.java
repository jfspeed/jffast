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
package cn.jfspeed.flow.resolver;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import cn.jfspeed.flowable.constant.FlowConstant;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysUserService;

/**
 * JfFastAssigneeResolver 单元测试。
 * <p>
 * 验证 role/dept 类型审批人解析逻辑：
 * - role → 查 sys_user.role_id like roleId + state=1
 * - dept → 查 sys_user.dept_id in deptIds + state=1
 * - 多角色/多部门去重
 * - value 为空/null/格式错误的边界处理
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JfFastAssigneeResolverTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private SysRoleService sysRoleService;

    @InjectMocks
    private JfFastAssigneeResolver resolver;

    @BeforeEach
    void setUp() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleKey("manager");
        when(sysRoleService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(role);
    }

    private SysUser buildUser(Long id, String roleId, Long deptId, Integer state) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRoleId(roleId);
        user.setDeptId(deptId);
        user.setState(state);
        return user;
    }

    @Nested
    @DisplayName("类型=role：按角色解析")
    class RoleType {

        @Test
        @DisplayName("单个角色应返回该角色下所有正常用户")
        void shouldResolveSingleRole() {
            List<SysUser> users = Arrays.asList(
                    buildUser(101L, "manager", 10L, 1),
                    buildUser(102L, "manager", 11L, 1));
            when(sysUserService.list(any(Wrapper.class))).thenReturn(users);

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager");

            assertEquals(2, result.size());
            assertTrue(result.contains("101"));
            assertTrue(result.contains("102"));
        }

        @Test
        @DisplayName("多个角色应合并去重")
        void shouldMergeAndDeduplicateMultipleRoles() {
            when(sysUserService.list(any(Wrapper.class)))
                    .thenReturn(Arrays.asList(
                            buildUser(101L, "manager", 10L, 1),
                            buildUser(102L, "manager,supervisor", 10L, 1)))
                    .thenReturn(Arrays.asList(
                            buildUser(102L, "manager,supervisor", 10L, 1),
                            buildUser(103L, "supervisor", 11L, 1)));

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager,supervisor");

            assertEquals(3, result.size());
            assertTrue(result.containsAll(Arrays.asList("101", "102", "103")));
            verify(sysUserService, times(2)).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("角色 key 先解析为角色 ID；纯数字按 ID 直接使用，不查表")
        void shouldResolveRoleKeyAndAcceptNumericId() {
            when(sysUserService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

            resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager");
            verify(sysRoleService).getOne(any(Wrapper.class), anyBoolean());

            resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "1001");
            verify(sysRoleService, times(1)).getOne(any(Wrapper.class), anyBoolean());
        }

        @Test
        @DisplayName("角色不存在时跳过、不查询用户，避免把流程配成无人可办")
        void shouldSkipUnknownRole() {
            when(sysRoleService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "ghost");

            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("角色下无用户应返回空列表")
        void shouldReturnEmptyWhenNoUser() {
            when(sysUserService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "nonexistent");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("类型=dept：按部门解析")
    class DeptType {

        @Test
        @DisplayName("单个部门应返回该部门下所有正常用户")
        void shouldResolveSingleDept() {
            List<SysUser> users = Arrays.asList(
                    buildUser(201L, "user", 100L, 1),
                    buildUser(202L, "user", 100L, 1));
            when(sysUserService.list(any(Wrapper.class))).thenReturn(users);

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_DEPT, "100");

            assertEquals(2, result.size());
            assertTrue(result.contains("201"));
            assertTrue(result.contains("202"));
        }

        @Test
        @DisplayName("多个部门应一次 IN 查询合并")
        void shouldResolveMultipleDeptsInOneQuery() {
            List<SysUser> users = Arrays.asList(
                    buildUser(201L, "user", 100L, 1),
                    buildUser(202L, "user", 200L, 1));
            when(sysUserService.list(any(Wrapper.class))).thenReturn(users);

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_DEPT, "100,200");

            assertEquals(2, result.size());
            verify(sysUserService, times(1)).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("部门 ID 格式错误应跳过但不抛异常")
        void shouldSkipInvalidDeptId() {
            when(sysUserService.list(any(Wrapper.class))).thenReturn(Collections.emptyList());

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_DEPT, "abc");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("混合合法/非法部门 ID 应只查合法的")
        void shouldOnlyQueryValidDeptIds() {
            when(sysUserService.list(any(Wrapper.class))).thenReturn(Collections.singletonList(
                    buildUser(301L, "user", 100L, 1)));

            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_DEPT, "100,abc,200");

            assertEquals(1, result.size());
            assertTrue(result.contains("301"));
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("value 为 null 应返回空列表")
        void shouldReturnEmptyWhenValueNull() {
            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, null);
            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("value 为空字符串应返回空列表")
        void shouldReturnEmptyWhenValueEmpty() {
            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "");
            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("value 为纯逗号应返回空列表")
        void shouldReturnEmptyWhenValueOnlyCommas() {
            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, " , , ");
            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("未知类型应返回空列表（不查询）")
        void shouldReturnEmptyForUnknownType() {
            List<String> result = resolver.resolveUserIds("unknown", "manager");
            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }

        @Test
        @DisplayName("user 类型不在本 resolver 处理范围（应返回空）")
        void shouldNotHandleUserType() {
            List<String> result = resolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_USER, "1,2,3");
            assertTrue(result.isEmpty());
            verify(sysUserService, never()).list(any(Wrapper.class));
        }
    }
}
