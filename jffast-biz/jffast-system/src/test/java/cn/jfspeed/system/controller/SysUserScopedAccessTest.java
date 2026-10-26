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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysResetPasswordRequest;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.domain.vo.SysUserVO;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.system.service.SysUserService;

/**
 * 用户管理读/写/删除/重置/解锁：目标一律先过数据范围存在性查询（R-03/N-03）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserScopedAccessTest {

    @Mock
    private SysUserService service;

    @Mock
    private SysRoleService sysRoleService;

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @InjectMocks
    private SysUserController controller;

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysRoleService", sysRoleService);
        login = mockStatic(LoginUtil.class);
        LoginUser user = new LoginUser();
        user.setId(10L);
        user.setUserName("operator");
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getRoleCodeList).thenReturn(java.util.List.of("common"));
        login.when(LoginUtil::getRoleIdList).thenReturn(java.util.List.of(2L));
        when(service.getScopedUser(anyLong())).thenThrow(new BusinessException(403, "用户不存在或无权操作"));
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    @Test
    @DisplayName("详情：范围外的目标直接返回 403，不加载用户")
    void detailShouldRejectOutOfScopeTarget() {
        R<SysUserVO> r = controller.getById(99L);
        assertEquals(403, r.getCode());
        assertEquals("用户不存在或无权操作", r.getMessage());
        verify(service, never()).getUser(anyLong(), any(), any());
    }

    @Test
    @DisplayName("修改：范围外的目标直接返回 403，不写库")
    void updateShouldRejectOutOfScopeTarget() {
        SysUserSaveRequest req = new SysUserSaveRequest();
        req.setId(99L);
        R r = controller.update(req);
        assertEquals(403, r.getCode());
        verify(service, never()).saveUser(any(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("修改：id 为空时在触达服务前拒绝")
    void updateShouldRejectMissingId() {
        R r = controller.update(new SysUserSaveRequest());
        assertEquals("用户ID不能为空", r.getMessage());
        verify(service, never()).getScopedUser(anyLong());
    }

    @Test
    @DisplayName("删除：范围外的目标直接返回 403，不删除")
    void deleteShouldRejectOutOfScopeTarget() {
        R r = controller.delete(99L);
        assertEquals(403, r.getCode());
        verify(service, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("删除自己：在触达服务前拒绝")
    void deleteSelfShouldBeRejected() {
        R r = controller.delete(10L);
        assertEquals("不能删除当前登录用户", r.getMessage());
        verify(service, never()).getScopedUser(anyLong());
        verify(service, never()).deleteUser(anyLong());
    }

    @Test
    @DisplayName("重置密码：范围外的目标直接返回 403，不更新密码")
    void resetPasswordShouldRejectOutOfScopeTarget() {
        SysResetPasswordRequest req = new SysResetPasswordRequest();
        req.setUserId(99L);
        R r = controller.resetPassword(req);
        assertEquals(403, r.getCode());
        verify(service, never()).update(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test
    @DisplayName("解锁：范围外的目标直接返回 403")
    void unlockShouldRejectOutOfScopeTarget() {
        R r = controller.unlock(99L);
        assertEquals(403, r.getCode());
        verify(service, never()).getUser(anyLong(), any(), any());
    }
}
