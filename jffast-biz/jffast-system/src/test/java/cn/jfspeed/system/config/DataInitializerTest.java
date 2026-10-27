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
package cn.jfspeed.system.config;

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
 * <p>
 * 与 {@link DataInitializer} 同住 {@code jffast-system}——测试跟着被测类走，否则被测类搬走后
 * 这里会在编译期就断掉。
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

    @Test
    @DisplayName("口令等于公开测试值时照常初始化（只打 WARN，不拒绝启动）")
    void shouldStillInitializeWhenPasswordIsThePublicTestValue() {
        // 公开测试值是各栈 yml 里 init-password 的开箱即用默认值，识别它只是为了打 WARN，
        // 绝不能因为它而跳过初始化——否则 admin 会永远停在占位值 "admin" 上。
        when(systemProperties.getInitPassword()).thenReturn("test1234");
        when(systemProperties.getPublicTestPassword()).thenReturn("test1234");
        SysUser admin = new SysUser();
        admin.setUserName("admin");
        admin.setPassword("admin");
        when(chain.one()).thenReturn(admin);

        initializer.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateById(captor.capture());
        assertTrue(EncryptionUtil.verifyPassword("test1234", captor.getValue().getPassword()));
    }

    @Test
    @DisplayName("未配置公开测试值时不做该判断，且仍能正常初始化")
    void shouldInitializeWhenPublicTestValueIsNotConfigured() {
        // publicTestPassword 可被显式置空（表示「本栈没有公开示例值」），此时不得空指针
        when(systemProperties.getPublicTestPassword()).thenReturn(null);
        SysUser admin = new SysUser();
        admin.setUserName("admin");
        admin.setPassword("admin");
        when(chain.one()).thenReturn(admin);

        initializer.run();

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateById(captor.capture());
        assertTrue(EncryptionUtil.verifyPassword("Init@123", captor.getValue().getPassword()));
    }
}
