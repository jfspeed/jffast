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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.service.SysRoleService;

/**
 * 创建角色：先落服务端 ID 再查重，客户端传入的 id 不能成为查重的排除项（既有行为观察的收口）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleCreateDuplicateCheckTest {

    private static final long CLIENT_SUPPLIED_ID = 99L;

    @Mock
    private SysRoleService service;

    @InjectMocks
    private SysRoleController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        when(service.resolveEffectiveRole(any(), any()))
                .thenReturn(new SysRoleService.EffectiveRole("ops", 4));
    }

    private static SysRoleRequest request() {
        SysRoleRequest req = new SysRoleRequest();
        req.setId(CLIENT_SUPPLIED_ID);
        req.setRoleKey("ops");
        req.setRoleName("运维");
        return req;
    }

    @Test
    @DisplayName("客户端传入的 id 不参与查重排除：新生成 ID 才会被排除")
    void clientSuppliedIdIsNotUsedAsExcludeId() {
        when(service.checkRoleNameAndKey(eq("运维"), eq("ops"), any())).thenReturn("角色标识已存在");

        R result = controller.insert(request());

        assertFalse(result.isSuccess());
        assertEquals("角色标识已存在", result.getMessage());
        ArgumentCaptor<Long> excludeId = ArgumentCaptor.forClass(Long.class);
        verify(service).checkRoleNameAndKey(eq("运维"), eq("ops"), excludeId.capture());
        assertNotNull(excludeId.getValue());
        assertNotEquals(CLIENT_SUPPLIED_ID, excludeId.getValue());
        verify(service, never()).createRole(any(SysRole.class));
    }

    @Test
    @DisplayName("查重通过：落库 ID 与查重排除 ID 一致，且写回有效 roleKey/dataScope")
    void generatedIdIsUsedForCreate() {
        when(service.checkRoleNameAndKey(eq("运维"), eq("ops"), any())).thenReturn(null);

        R result = controller.insert(request());

        assertEquals(200, result.getCode());
        ArgumentCaptor<Long> excludeId = ArgumentCaptor.forClass(Long.class);
        verify(service).checkRoleNameAndKey(eq("运维"), eq("ops"), excludeId.capture());
        ArgumentCaptor<SysRole> created = ArgumentCaptor.forClass(SysRole.class);
        verify(service).createRole(created.capture());
        assertEquals(excludeId.getValue(), created.getValue().getId());
        assertEquals("ops", created.getValue().getRoleKey());
        assertEquals(4, created.getValue().getDataScope());
    }
}
