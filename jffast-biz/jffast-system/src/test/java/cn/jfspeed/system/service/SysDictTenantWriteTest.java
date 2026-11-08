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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.entity.SysDict;
import cn.jfspeed.system.mapper.SysDictMapper;

/**
 * 字典写侧的租户归属：新建字典必须落当前会话租户，不能落成 {@code tenant_id = NULL}
 * 的平台行（平台行在云版对所有租户可见）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDictTenantWriteTest {

    @Mock
    private SysDictMapper baseMapper;

    @InjectMocks
    private SysDictService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(baseMapper.insert(any(SysDict.class))).thenReturn(1);
    }

    private static LoginUser loginUserOfTenant(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(100L);
        user.setTenantId(tenantId);
        return user;
    }

    private SysDict captureInserted() {
        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(baseMapper).insert(captor.capture());
        return captor.getValue();
    }

    private static SysDict newDict() {
        SysDict dict = new SysDict();
        dict.setName("启用状态");
        dict.setCode("sys_status");
        return dict;
    }

    @Test
    @DisplayName("会话租户非空：新建字典落该租户，不再落成平台行")
    void saveFillsSessionTenant() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUserOfTenant(7L));

            service.save(newDict());

            assertEquals(7L, captureInserted().getTenantId());
        }
    }

    @Test
    @DisplayName("无登录态：保持 null（启动期/匿名字典不强行挂租户）")
    void saveKeepsNullWithoutLogin() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(null);

            service.save(newDict());

            assertNull(captureInserted().getTenantId());
        }
    }

    @Test
    @DisplayName("显式指定的 tenantId 不被会话租户覆盖")
    void saveKeepsExplicitTenant() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUserOfTenant(7L));
            SysDict dict = newDict();
            dict.setTenantId(9L);

            service.save(dict);

            assertEquals(9L, captureInserted().getTenantId());
        }
    }
}
