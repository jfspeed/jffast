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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;

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
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.SysFileService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件记录的归属校验（无需 MySQL）。
 * <p>
 * 这个模块的接口只有功能权限（{@code system:file:view/download/delete}），此前完全没有
 * 数据归属：任何一个被授予这些权限的非超管，改一下 id 就能下载或删除别人上传的文件；
 * 分页接口还会把他人记录的 url 直接回给他，而 url 又挂在 {@code /upload/**} 免登录静态映射上
 * ——查看权限因此等于全量文件的读取权限。2026-10-26 第三轮复核发现，归属口径与
 * {@code /sys/task} 对齐（超管全量、普通用户按 create_by）。
 * <p>
 * 越权必须与「记录不存在」同表现，否则返回值差异本身就成了一种枚举手段。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysFileOwnershipTest {

    private static final long OWNER_ID = 7L;
    private static final long OTHER_ID = 8L;

    @Mock
    private SysFileService service;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private SysFileController controller;

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "fileStorageService", fileStorageService);
        login = mockStatic(LoginUtil.class);
        login.when(LoginUtil::getLoginUserId).thenReturn(OWNER_ID);
        login.when(LoginUtil::isSuperAdmin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    private static SysFile file(Long id, Long createBy) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setUrl(id + "/report.xlsx");
        file.setFileName("report.xlsx");
        file.setSize(10L);
        file.setCreateBy(createBy);
        return file;
    }

    private HttpServletResponse response() throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenReturn(mock(ServletOutputStream.class));
        return response;
    }

    @Test
    @DisplayName("下载他人的文件：与「文件不存在」同一提示，且不读存储字节")
    void downloadOtherUsersFileIsDenied() throws Exception {
        when(service.getById(9L)).thenReturn(file(9L, OTHER_ID));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(9L, response()));

        assertEquals("文件不存在或已清理", ex.getMessage());
        verify(fileStorageService, never()).downloadFile(anyString());
    }

    @Test
    @DisplayName("下载本人文件不受影响（反向对照，证明归属校验不是恒拒）")
    void downloadOwnFileStillWorks() throws Exception {
        when(service.getById(9L)).thenReturn(file(9L, OWNER_ID));
        when(fileStorageService.downloadFile("9/report.xlsx")).thenReturn(new byte[]{1, 2, 3});

        controller.download(9L, response());

        verify(fileStorageService).downloadFile("9/report.xlsx");
    }

    @Test
    @DisplayName("超管下载他人文件放行（归属过滤只对普通用户生效）")
    void superAdminDownloadsAnyFile() throws Exception {
        login.when(LoginUtil::isSuperAdmin).thenReturn(true);
        when(service.getById(9L)).thenReturn(file(9L, OTHER_ID));
        when(fileStorageService.downloadFile("9/report.xlsx")).thenReturn(new byte[]{1});

        controller.download(9L, response());

        verify(fileStorageService).downloadFile("9/report.xlsx");
    }

    @Test
    @DisplayName("删除他人的文件：拒绝，既不删存储也不删记录")
    void deleteOtherUsersFileIsDenied() {
        when(service.getById(9L)).thenReturn(file(9L, OTHER_ID));

        assertThrows(BusinessException.class, () -> controller.delete(9L));

        verify(fileStorageService, never()).delete(anyString());
        verify(service, never()).removeById(9L);
    }

    @Test
    @DisplayName("批量删除混入他人 id：只删本人的，他人的静默跳过")
    void batchDeleteSkipsForeignFiles() {
        when(service.getById(1L)).thenReturn(file(1L, OWNER_ID));
        when(service.getById(2L)).thenReturn(file(2L, OTHER_ID));

        controller.deleteBatch(Arrays.asList(1L, 2L));

        verify(fileStorageService).delete("1/report.xlsx");
        verify(service).removeById(1L);
        verify(fileStorageService, never()).delete("2/report.xlsx");
        verify(service, never()).removeById(2L);
    }

    @Test
    @DisplayName("分页：普通用户按 create_by 过滤，超管传 null 取全量")
    void pageIsScopedToOwner() {
        SysFileQueryRequest query = new SysFileQueryRequest();

        controller.page(query);
        verify(service).page(query, OWNER_ID);

        login.when(LoginUtil::isSuperAdmin).thenReturn(true);
        controller.page(query);
        verify(service).page(query, null);
    }

    @Test
    @DisplayName("取不到登录用户 ID：拒绝，不得退化成「无归属限制」")
    void unresolvedUserIdFailsClosed() {
        // 会话失效 / Session 里没有 user / 读会话抛异常被吞 —— 这三种都会让 getLoginUserId() 返回 null。
        // 若把 null 当成「超管全量」，一次会话读取失败就等于给全量文件开了后门（与本轮要堵的洞同型）。
        login.when(LoginUtil::getLoginUserId).thenReturn(null);
        when(service.getById(9L)).thenReturn(file(9L, OTHER_ID));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(9L, response()));

        assertEquals(401, ex.getCode(), "身份不可解析应按未登录拒绝，实际：" + ex.getCode());
        verify(fileStorageService, never()).downloadFile(anyString());
        verify(service, never()).removeById(9L);
    }

    @Test
    @DisplayName("取不到登录用户 ID：分页也拒绝，不能返回全量列表")
    void unresolvedUserIdFailsClosedOnPage() {
        login.when(LoginUtil::getLoginUserId).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.page(new SysFileQueryRequest()));

        verify(service, never()).page(any(SysFileQueryRequest.class), any());
    }
}
