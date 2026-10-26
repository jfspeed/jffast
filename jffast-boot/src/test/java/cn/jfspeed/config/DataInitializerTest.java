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
package cn.jfspeed.config;

import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysUserService;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DataInitializer 测试：管理员初始密码仅在仍为明文默认值时被加密重置。
 *
 * @author jffast
 */
@DisplayName("DataInitializer 管理员初始密码初始化测试")
@SuppressWarnings({"unchecked", "rawtypes"})
class DataInitializerTest {

    private SysUserService sysUserService;
    private LambdaQueryChainWrapper<SysUser> chain;
    private JfFastSystemProperties systemProperties;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        sysUserService = mock(SysUserService.class);
        chain = mock(LambdaQueryChainWrapper.class);
        systemProperties = mock(JfFastSystemProperties.class);
        when(systemProperties.getInitPassword()).thenReturn("Init@123");
        when(sysUserService.lambdaQuery()).thenReturn(chain);
        when(chain.eq(any(), any())).thenReturn(chain);
        initializer = new DataInitializer(sysUserService, systemProperties);
    }

    @Test
    @DisplayName("admin 不存在时不做任何更新")
    void shouldDoNothingWhenAdminMissing() {
        when(chain.one()).thenReturn(null);
        initializer.run();
        verify(sysUserService, never()).updateById(any());
    }

    @Test
    @DisplayName("admin 密码仍是明文默认值时应加密重置并更新")
    void shouldEncryptPlainDefaultPassword() {
        SysUser admin = new SysUser();
        admin.setUserName("admin");
        admin.setPassword("admin");
        when(chain.one()).thenReturn(admin);

        initializer.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateById(captor.capture());
        String encrypted = captor.getValue().getPassword();
        assertNotEquals("admin", encrypted);
        assertTrue(EncryptionUtil.verifyPassword("Init@123", encrypted));
    }

    @Test
    @DisplayName("admin 密码已被加密时不应更新")
    void shouldSkipWhenPasswordAlreadyEncrypted() {
        SysUser admin = new SysUser();
        admin.setUserName("admin");
        admin.setPassword(EncryptionUtil.getEncryptPassword("already@123"));
        when(chain.one()).thenReturn(admin);

        initializer.run();

        verify(sysUserService, never()).updateById(any());
    }
}
