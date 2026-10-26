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
package cn.jfspeed.system.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;

/**
 * 用户写侧授权：自我改部门/角色一律拒绝，非超管不得移动他人部门（N-08）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserDeptChangeAuthorizationTest {

    private static final Long SELF_ID = 10L;
    private static final Long TARGET_ID = 20L;

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @InjectMocks
    private SysUserController controller;

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginUtil.class);
        login.when(LoginUtil::getLoginUser).thenReturn(loginUser(SELF_ID));
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static LoginUser loginUser(Long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUserName("operator");
        return user;
    }

    private void asSuperAdmin() {
        login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("super_admin"));
        login.when(LoginUtil::getRoleIdList).thenReturn(List.of(1L));
    }

    private void asCommonUser() {
        login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("common"));
        login.when(LoginUtil::getRoleIdList).thenReturn(List.of(2L));
    }

    private static SysUser target(Long id, String userName, Long deptId, Long... roleIds) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUserName(userName);
        user.setDeptId(deptId);
        List<SysRole> roles = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysRole role = new SysRole();
            role.setId(roleId);
            roles.add(role);
        }
        user.setRoleList(roles);
        return user;
    }

    private static SysUserSaveRequest request(Long deptId, Long... roleIds) {
        SysUserSaveRequest req = new SysUserSaveRequest();
        req.setDeptId(deptId);
        if (roleIds != null) {
            List<SysUserSaveRequest.IdRef> roleRefs = new ArrayList<>();
            for (Long roleId : roleIds) {
                SysUserSaveRequest.IdRef ref = new SysUserSaveRequest.IdRef();
                ref.setId(roleId);
                roleRefs.add(ref);
            }
            req.setRoleList(roleRefs);
        }
        return req;
    }

    private String validate(SysUser target, SysUserSaveRequest req) {
        return ReflectionTestUtils.invokeMethod(controller, "validateUpdateAuthorization", target, req);
    }

    @Test
    @DisplayName("自己改自己的部门：拒绝（含超管）")
    void selfDeptChangeShouldBeRejected() {
        asSuperAdmin();
        String error = validate(target(SELF_ID, "self", 100L), request(200L));
        assertEquals("不能修改自己的部门", error);
    }

    @Test
    @DisplayName("自己未提交 deptId / deptId 不变：放行")
    void selfDeptUnchangedShouldPass() {
        asCommonUser();
        assertNull(validate(target(SELF_ID, "self", 100L), request(null)));
        assertNull(validate(target(SELF_ID, "self", 100L), request(100L)));
    }

    @Test
    @DisplayName("自己改自己的角色集合：拒绝")
    void selfRoleChangeShouldBeRejected() {
        asSuperAdmin();
        String error = validate(target(SELF_ID, "self", 100L, 1L), request(null, 2L));
        assertEquals("不能修改自己的角色或部门", error);
    }

    @Test
    @DisplayName("角色集合顺序不同但内容相同：视为未变更")
    void sameRoleSetIsOrderInsensitive() {
        asSuperAdmin();
        SysUser target = target(TARGET_ID, "target", 100L, 2L, 3L);
        assertNull(validate(target, request(null, 3L, 2L)));
    }

    @Test
    @DisplayName("非超管移动他人部门：拒绝")
    void nonSuperMovingOtherDeptShouldBeRejected() {
        asCommonUser();
        String error = validate(target(TARGET_ID, "target", 100L), request(200L));
        assertEquals("无权修改该用户部门", error);
    }

    @Test
    @DisplayName("非超管改他人角色：拒绝")
    void nonSuperChangingOtherRolesShouldBeRejected() {
        asCommonUser();
        String error = validate(target(TARGET_ID, "target", 100L, 2L), request(null, 3L));
        assertEquals("无权分配角色或部门", error);
    }

    @Test
    @DisplayName("超管移动他人部门/角色：放行")
    void superAdminMayMoveOtherUser() {
        asSuperAdmin();
        assertNull(validate(target(TARGET_ID, "target", 100L, 2L), request(200L, 3L)));
    }

    @Test
    @DisplayName("超管也不得改内置 admin 账号的角色")
    void adminRoleShouldBeImmutable() {
        asSuperAdmin();
        String error = validate(target(TARGET_ID, "admin", 100L, 1L), request(null, 2L));
        assertEquals("内置超管账号的角色不允许修改", error);
    }

    @Test
    @DisplayName("未提交集合字段（null）不触发授权变更校验")
    void absentCollectionsShouldPass() {
        asCommonUser();
        SysUserSaveRequest req = new SysUserSaveRequest();
        assertNull(validate(target(TARGET_ID, "target", 100L, 2L), req));
    }

    @Test
    @DisplayName("deptList 变更同样受自我/非超管限制")
    void deptListChangeShouldFollowSameRules() {
        asCommonUser();
        SysUserSaveRequest req = new SysUserSaveRequest();
        SysUserSaveRequest.IdRef ref = new SysUserSaveRequest.IdRef();
        ref.setId(100L);
        req.setDeptList(List.of(ref));
        SysDept dept = new SysDept();
        dept.setId(200L);
        SysUser target = target(TARGET_ID, "target", 100L);
        target.setDeptList(List.of(dept));
        assertEquals("无权分配角色或部门", validate(target, req));
    }
}
