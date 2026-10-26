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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.security.util.DataPermissionSqlBuilder;
import cn.jfspeed.system.entity.SysTask;
import cn.jfspeed.system.service.SysTaskContext;
import cn.jfspeed.system.service.SysTaskRunner;
import cn.jfspeed.system.service.SysTaskService;
import cn.jfspeed.system.service.SysUserService;

/**
 * 异步导出：数据权限片段与操作者在提交前快照，解析失败不创建任务（R-01/N-01）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserAsyncExportDataScopeTest {

    @Mock
    private SysUserService service;

    @Mock
    private SysTaskService sysTaskService;

    @Mock
    private SysTaskRunner sysTaskRunner;

    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @InjectMocks
    private SysUserController controller;

    private MockedStatic<LoginUtil> login;
    private MockedStatic<DataPermissionSqlBuilder> builder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysTaskService", sysTaskService);
        ReflectionTestUtils.setField(controller, "sysTaskRunner", sysTaskRunner);

        login = mockStatic(LoginUtil.class);
        LoginUser user = new LoginUser();
        user.setId(10L);
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getLoginUserId).thenReturn(10L);

        builder = mockStatic(DataPermissionSqlBuilder.class);
        SysTask task = new SysTask();
        task.setId(77L);
        when(sysTaskService.createTask(anyString(), anyString(), anyString())).thenReturn(task);
    }

    @AfterEach
    void tearDown() {
        builder.close();
        login.close();
    }

    private void resolveReturns(String sql) {
        builder.when(() -> DataPermissionSqlBuilder.resolve(
                        SysUserService.class, "listForExport", Map.class, int.class))
                .thenReturn(sql);
    }

    @Test
    @DisplayName("提交前快照数据权限片段与操作者，工作线程按快照运行")
    void snapshotCapturedBeforeSubmit() {
        resolveReturns("d.id = '12' and u.id = 10");

        controller.exportAsync(Map.of());

        ArgumentCaptor<SysTaskContext> captor = ArgumentCaptor.forClass(SysTaskContext.class);
        verify(sysTaskRunner).submit(captor.capture(), any(Runnable.class));
        assertEquals(10L, captor.getValue().operatorId());
        assertEquals("d.id = '12' and u.id = 10", captor.getValue().dataScopeSql());
    }

    @Test
    @DisplayName("注解元数据解析失败：中止且不创建任务")
    void resolveFailureAbortsWithoutTask() {
        builder.when(() -> DataPermissionSqlBuilder.resolve(
                        SysUserService.class, "listForExport", Map.class, int.class))
                .thenThrow(new IllegalStateException("数据权限注解缺失"));

        assertThrows(IllegalStateException.class, () -> controller.exportAsync(Map.of()));

        verify(sysTaskService, never()).createTask(anyString(), anyString(), anyString());
        verify(sysTaskRunner, never()).submit(any(SysTaskContext.class), any(Runnable.class));
    }
}
