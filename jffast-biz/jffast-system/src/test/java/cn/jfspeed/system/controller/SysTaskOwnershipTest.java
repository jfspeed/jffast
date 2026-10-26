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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysTaskQueryRequest;
import cn.jfspeed.system.entity.SysTask;
import cn.jfspeed.system.service.SysTaskRunner;
import cn.jfspeed.system.service.SysTaskService;

/**
 * 异步任务归属（R-04）：普通用户仅能访问本人任务的详情/下载/删除，文件路径不得越界，
 * 详情不得泄露服务器路径。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysTaskOwnershipTest {

    private static final Long OWNER_ID = 10L;

    @Mock
    private SysTaskService service;

    @Mock
    private SysTaskRunner sysTaskRunner;

    @InjectMocks
    private SysTaskController controller;

    @TempDir
    Path taskDir;

    @TempDir
    Path outsideDir;

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysTaskRunner", sysTaskRunner);
        when(sysTaskRunner.taskDir()).thenReturn(taskDir);
        login = mockStatic(LoginUtil.class);
        LoginUser user = new LoginUser();
        user.setId(OWNER_ID);
        user.setUserName("operator");
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        login.when(LoginUtil::getLoginUserId).thenReturn(OWNER_ID);
        login.when(LoginUtil::isSuperAdmin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysTask task(Long id, Long createBy, String filePath) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setCreateBy(createBy);
        task.setFileName("用户数据.xlsx");
        task.setFilePath(filePath);
        return task;
    }

    @Test
    @DisplayName("分页：普通用户按 create_by 过滤，超管不过滤")
    void pageFiltersByOwner() {
        SysTaskQueryRequest query = new SysTaskQueryRequest();
        controller.page(query);
        verify(service).page(query, OWNER_ID);

        login.when(LoginUtil::isSuperAdmin).thenReturn(true);
        controller.page(query);
        verify(service).page(query, null);
    }

    @Test
    @DisplayName("详情：他人任务返回 403；本人任务不暴露 filePath")
    void detailChecksOwnershipAndHidesPath() {
        when(service.getById(1L)).thenReturn(task(1L, 99L, null));
        R other = controller.getById(1L);
        assertEquals(403, other.getCode());

        SysTask mine = task(2L, OWNER_ID, taskDir.resolve("export.xlsx").toString());
        when(service.getById(2L)).thenReturn(mine);
        R<SysTask> own = controller.getById(2L);
        assertNull(own.getData().getFilePath());
    }

    @Test
    @DisplayName("超管可读取他人任务")
    void superAdminMayReadOthers() {
        login.when(LoginUtil::isSuperAdmin).thenReturn(true);
        when(service.getById(1L)).thenReturn(task(1L, 99L, null));
        assertEquals(true, controller.getById(1L).isSuccess());
    }

    @Test
    @DisplayName("下载：他人任务 403，路径越界或文件缺失按不存在处理")
    void downloadRejectsForeignAndEscapedPaths() throws Exception {
        when(service.getById(1L)).thenReturn(task(1L, 99L, null));
        R foreign = (R) controller.download(1L, new MockHttpServletResponse());
        assertEquals(403, foreign.getCode());

        when(service.getById(2L)).thenReturn(task(2L, OWNER_ID, taskDir.resolve("missing.xlsx").toString()));
        R missing = (R) controller.download(2L, new MockHttpServletResponse());
        assertEquals("文件不存在或已清理", missing.getMessage());

        Path outside = taskDir.getParent().resolve("evil.xlsx");
        when(service.getById(3L)).thenReturn(task(3L, OWNER_ID, outside.toString()));
        R escaped = (R) controller.download(3L, new MockHttpServletResponse());
        assertEquals("文件不存在或已清理", escaped.getMessage());
    }

    @Test
    @DisplayName("删除：他人任务 403 且不删除记录；超管可删除")
    void deleteChecksOwnership() {
        when(service.getById(1L)).thenReturn(task(1L, 99L, null));
        R foreign = controller.delete(1L);
        assertEquals(403, foreign.getCode());
        verify(service, never()).removeById(any(Long.class));

        login.when(LoginUtil::isSuperAdmin).thenReturn(true);
        when(service.getById(1L)).thenReturn(task(1L, 99L, null));
        assertEquals(true, controller.delete(1L).isSuccess());
        verify(service).removeById(1L);
    }

    @Test
    @DisplayName("删除：任务目录内文件随记录删除，目录外文件不得删除（P3-14/P3-19）")
    void deleteRemovesOnlyTaskDirFiles() throws Exception {
        Path inside = Files.writeString(taskDir.resolve("result.xlsx"), "data");
        when(service.getById(2L)).thenReturn(task(2L, OWNER_ID, inside.toString()));

        assertEquals(true, controller.delete(2L).isSuccess());

        verify(service).removeById(2L);
        assertFalse(Files.exists(inside), "任务目录内的结果文件必须随记录删除");

        Path outside = Files.writeString(outsideDir.resolve("escape.xlsx"), "keep");
        when(service.getById(3L)).thenReturn(task(3L, OWNER_ID, outside.toString()));

        assertEquals(true, controller.delete(3L).isSuccess());

        verify(service).removeById(3L);
        assertTrue(Files.exists(outside), "目录外文件不得因越界 file_path 被删除");
    }
}
