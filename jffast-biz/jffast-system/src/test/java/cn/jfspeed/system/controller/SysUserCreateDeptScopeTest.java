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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.security.util.RSAUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysDeptService;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysUserService;

/**
 * 创建用户的部门校验：非超管必须指定可见部门，超管只校验存在性。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserCreateDeptScopeTest {

    private static final Long SELF_ID = 10L;

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

    private static void asSuperAdmin(MockedStatic<LoginUtil> login) {
        LoginUser user = new LoginUser();
        user.setId(SELF_ID);
        user.setUserName("admin");
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("super_admin"));
        login.when(LoginUtil::getRoleIdList).thenReturn(List.of(1L));
    }

    private static void asCommonUser(MockedStatic<LoginUtil> login) {
        LoginUser user = new LoginUser();
        user.setId(SELF_ID);
        user.setUserName("operator");
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("common"));
        login.when(LoginUtil::getRoleIdList).thenReturn(List.of(2L));
    }

    private static SysUserSaveRequest request(Long deptId) {
        SysUserSaveRequest req = new SysUserSaveRequest();
        req.setUserName("probe_create_scope");
        req.setPassword("cipher");
        req.setDeptId(deptId);
        return req;
    }

    @Test
    @DisplayName("非超管未指定 deptId：403 且不落库")
    void nonSuperCreateWithoutDeptIdRejected403() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);

            R result = controller.insert(request(null));

            assertEquals(403, result.getCode());
            assertEquals("请指定数据范围内的部门", result.getMessage());
            verify(service, never()).saveUser(any(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    @DisplayName("非超管指定范围外 deptId：403 且不落库")
    void nonSuperCreateWithOutOfScopeDeptRejected403() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asCommonUser(login);
            when(sysDeptScopeQuery.findVisibleDept(99L)).thenReturn(null);

            R result = controller.insert(request(99L));

            assertEquals(403, result.getCode());
            assertEquals("部门不存在或无权使用该部门", result.getMessage());
            verify(service, never()).saveUser(any(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    @DisplayName("非超管指定范围内 deptId：创建成功")
    void nonSuperCreateWithVisibleDeptSucceeds() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RSAUtil> rsa = mockStatic(RSAUtil.class);
             MockedStatic<EncryptionUtil> encryption = mockStatic(EncryptionUtil.class)) {
            asCommonUser(login);
            SysDept dept = new SysDept();
            dept.setId(10L);
            dept.setDeptName("技术部");
            when(sysDeptScopeQuery.findVisibleDept(10L)).thenReturn(dept);
            when(service.checkUserNameAndPhone(any(), any(), any())).thenReturn(false);
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp("cipher")).thenReturn("Init@1234");
            encryption.when(() -> EncryptionUtil.getEncryptPassword("Init@1234")).thenReturn("hash");

            R result = controller.insert(request(10L));

            assertEquals(200, result.getCode());
            verify(service).saveUser(any(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    @DisplayName("超管指定不存在 deptId：400 且不落库")
    void superCreateWithMissingDeptRejected400() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            asSuperAdmin(login);
            when(sysDeptService.getById(99L)).thenReturn(null);

            R result = controller.insert(request(99L));

            assertEquals(400, result.getCode());
            assertEquals("部门不存在", result.getMessage());
            verify(service, never()).saveUser(any(), anyBoolean(), anyBoolean());
        }
    }
}
