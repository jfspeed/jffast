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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

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

import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.security.util.RSAUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysPasswordRequest;

/**
 * updatePassword 安全边界用例。
 * <p>
 * 防越权契约：目标用户只能是登录态本人，请求 DTO 不再接受 userId；
 * 解密失败、与初始密码相同等分支在触达数据库前即返回。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserControllerUpdatePasswordTest {

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @Mock
    private JfFastSystemProperties jfFastSystemProperties;

    @InjectMocks
    private SysUserController controller;

    @BeforeEach
    void setUp() {
        // @InjectMocks 走构造器注入后不再做字段注入，@Resource 字段需手动塞入
        ReflectionTestUtils.setField(controller, "jfFastSystemProperties", jfFastSystemProperties);
        when(jfFastSystemProperties.getInitPassword()).thenReturn("Init@1234");
        when(loginPolicyProvider.getIfAvailable()).thenReturn(null);
    }

    private static LoginUser loginAs(Long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUserName("admin");
        return user;
    }

    private static SysPasswordRequest request(String oldPassword, String newPassword) {
        SysPasswordRequest req = new SysPasswordRequest();
        req.setOldPassword(oldPassword);
        req.setNewPassword(newPassword);
        return req;
    }

    @Test
    @DisplayName("请求 DTO 不得包含 userId（目标用户只取登录态）")
    void requestDtoShouldNotExposeUserId() {
        assertThrows(NoSuchFieldException.class,
                () -> SysPasswordRequest.class.getDeclaredField("userId"));
    }

    @Test
    @DisplayName("解密失败返回「数据解密失败」且不触达用户查询")
    void shouldRejectWhenDecryptFails() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RSAUtil> rsa = mockStatic(RSAUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginAs(1L));
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp(org.mockito.ArgumentMatchers.anyString()))
                    .thenReturn(null);

            R<?> r = controller.updatePassword(request("old-cipher", "new-cipher"));

            assertEquals("数据解密失败", r.getMessage());
        }
    }

    @Test
    @DisplayName("新密码与初始密码相同时应拒绝")
    void shouldRejectInitialPassword() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RSAUtil> rsa = mockStatic(RSAUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginAs(1L));
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp("old-cipher")).thenReturn("Old@1234");
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp("init-cipher")).thenReturn("Init@1234");

            R<?> r = controller.updatePassword(request("old-cipher", "init-cipher"));

            assertEquals("新密码不可与初始密码相同", r.getMessage());
        }
    }

    @Test
    @DisplayName("弱密码应被强度校验拒绝")
    void shouldRejectWeakPassword() throws Exception {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RSAUtil> rsa = mockStatic(RSAUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginAs(1L));
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp("old-cipher")).thenReturn("Old@1234");
            rsa.when(() -> RSAUtil.decryptPwdWithTimestamp("weak-cipher")).thenReturn("123456");

            R<?> r = controller.updatePassword(request("old-cipher", "weak-cipher"));

            assertEquals("密码强度不足：需8-20位，包含大小写字母、数字和特殊字符", r.getMessage());
        }
    }
}
