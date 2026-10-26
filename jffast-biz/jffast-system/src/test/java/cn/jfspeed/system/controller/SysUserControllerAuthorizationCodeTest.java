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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysResetPasswordRequest;
import cn.jfspeed.system.domain.dto.SysUserProfileRequest;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysDeptService;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysUserService;

/**
 * P3-22：用户侧授权拒绝统一 HTTP 200 + business code 403（九处）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserControllerAuthorizationCodeTest {

    private static final Long SELF_ID = 10L;
    private static final Long TARGET_ID = 20L;

    @Mock
    private SysUserService service;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysDeptScopeQuery sysDeptScopeQuery;
    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    private SysUserController controller;

    @BeforeEach
    void setUp() {
        controller = new SysUserController(loginPolicyProvider);
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysRoleService", sysRoleService);
        ReflectionTestUtils.setField(controller, "sysDeptService", sysDeptService);
        ReflectionTestUtils.setField(controller, "sysDeptScopeQuery", sysDeptScopeQuery);
    }

    private static void asCommonUser(MockedStatic<LoginUtil> login) {
        LoginUser user = new LoginUser();
        user.setId(SELF_ID);
        user.setUserName("operator");
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("common"));
        login.when(LoginUtil::getRoleIdList).thenReturn(List.of(2L));
    }

    private static SysUser target(String userName) {
        SysUser user = new SysUser();
        user.setId(TARGET_ID);
        user.setUserName(userName);
        user.setRoleList(List.of());
        return user;
    }

    private static SysUserSaveRequest insertRequest(String userName, Long deptId) {
        SysUserSaveRequest req = new SysUserSaveRequest();
        req.setUserName(userName);
        req.setPassword("cipher");
        req.setDeptId(deptId);
        return req;
    }

    private static SysUserSaveRequest.IdRef ref(Long id) {
        SysUserSaveRequest.IdRef ref = new SysUserSaveRequest.IdRef();
        ref.setId(id);
        return ref;
    }

    private static SysUserProfileRequest profileRequest(Long id, String phone) {
        SysUserProfileRequest req = new SysUserProfileRequest();
        req.setId(id);
        req.setPhone(phone);
        return req;
    }

    @Test
    @DisplayName("insert：非超管分配角色/部门 → 403")
    void insertAuthorizationDenied403() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            SysUserSaveRequest req = insertRequest("probe_auth_1", null);
            req.setRoleList(List.of(ref(2L)));

            R result = controller.insert(req);

            assertEquals(403, result.getCode());
            assertEquals("无权分配角色或部门", result.getMessage());
        }
    }

    @Test
    @DisplayName("insert：非超管使用超管用户名 → 403")
    void insertSuperAdminUserNameDenied403() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            SysDept dept = new SysDept();
            dept.setId(10L);
            when(sysDeptScopeQuery.findVisibleDept(10L)).thenReturn(dept);

            R result = controller.insert(insertRequest("admin", 10L));

            assertEquals(403, result.getCode());
            assertEquals("无权创建超级管理员账号", result.getMessage());
        }
    }

    @Test
    @DisplayName("update：非超管操作超管账号 → 403（validateManageable）")
    void updateManageableDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("admin"));

            SysUserSaveRequest req = insertRequest("admin", null);
            req.setId(TARGET_ID);

            R result = controller.update(req);

            assertEquals(403, result.getCode());
            assertEquals("无权操作超级管理员账号", result.getMessage());
        }
    }

    @Test
    @DisplayName("update：非超管改他人角色 → 403（validateUpdateAuthorization）")
    void updateAuthorizationDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("user2"));
            when(sysRoleService.loadOperatorContext()).thenReturn(
                    new SysRoleService.OperatorContext(SELF_ID, List.of(2L), 10, Set.of(), false));
            SysUserSaveRequest req = insertRequest("user2", null);
            req.setId(TARGET_ID);
            req.setRoleList(List.of(ref(3L)));

            R result = controller.update(req);

            assertEquals(403, result.getCode());
            assertEquals("无权分配角色或部门", result.getMessage());
        }
    }

    @Test
    @DisplayName("update：非超管使用超管用户名 → 403")
    void updateSuperAdminUserNameDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("user2"));
            when(sysRoleService.loadOperatorContext()).thenReturn(
                    new SysRoleService.OperatorContext(SELF_ID, List.of(2L), 10, Set.of(), false));
            SysUserSaveRequest req = insertRequest("admin", null);
            req.setId(TARGET_ID);

            R result = controller.update(req);

            assertEquals(403, result.getCode());
            assertEquals("无权使用超级管理员用户名", result.getMessage());
        }
    }

    @Test
    @DisplayName("delete：删除当前登录用户 → 403")
    void deleteSelfDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);

            R result = controller.delete(SELF_ID);

            assertEquals(403, result.getCode());
            assertEquals("不能删除当前登录用户", result.getMessage());
        }
    }

    @Test
    @DisplayName("delete：非超管删除超管账号 → 403")
    void deleteManageableDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("admin"));

            R result = controller.delete(TARGET_ID);

            assertEquals(403, result.getCode());
            assertEquals("无权操作超级管理员账号", result.getMessage());
        }
    }

    @Test
    @DisplayName("resetPassword：非超管重置超管账号 → 403")
    void resetPasswordManageableDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("admin"));
            SysResetPasswordRequest req = new SysResetPasswordRequest();
            req.setUserId(TARGET_ID);

            R result = controller.resetPassword(req);

            assertEquals(403, result.getCode());
            assertEquals("无权操作超级管理员账号", result.getMessage());
        }
    }

    @Test
    @DisplayName("unlock：非超管解锁超管账号 → 403")
    void unlockManageableDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(service.getScopedUser(TARGET_ID)).thenReturn(target("admin"));

            R result = controller.unlock(TARGET_ID);

            assertEquals(403, result.getCode());
            assertEquals("无权操作超级管理员账号", result.getMessage());
        }
    }

    @Test
    @DisplayName("updateProfile：改他人资料（资源归属越权）→ 403")
    void updateProfileOwnershipDenied403() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);

            R result = controller.updateProfile(profileRequest(TARGET_ID, null));

            assertEquals(403, result.getCode());
            assertEquals("只能修改本人资料", result.getMessage());
        }
    }

    @Test
    @DisplayName("updateProfile：本人填写已被占用手机号 → 400（业务校验）")
    void updateProfilePhoneOccupied400() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            String occupiedPhone = "13800001111";
            when(service.checkUserNameAndPhone(null, occupiedPhone, SELF_ID)).thenReturn(true);

            R result = controller.updateProfile(profileRequest(SELF_ID, occupiedPhone));

            assertEquals(400, result.getCode());
            assertEquals("手机号码已被占用", result.getMessage());
        }
    }
}
