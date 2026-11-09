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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.FileUrlSignService;
import cn.jfspeed.system.service.SysFileService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 受控下载的归属与租户隔离（AC-07）。
 *
 * <p>本轮把「文件读取」从「登录即可读任意路径」收口到「必须过归属 + 租户校验」，
 * 因此控制器这一层的行为必须被钉死：
 * <ul>
 *   <li>本人 ⇒ 放行（流式输出，不再整文件读进内存）；</li>
 *   <li>他人 / 跨租户 / 记录不存在 ⇒ 一律「文件不存在或已清理」，**不区分原因**；
 *       否则可以用返回值差异枚举出哪些文件存在（防枚举）；</li>
 *   <li>超管只豁免「上传者」维度，**不豁免租户维度**——否则跨租户隔离靠「超管手滑」维持。</li>
 * </ul>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysFileControllerOwnerTest {

    private static final long OWNER_ID = 7L;
    private static final long OTHER_ID = 8L;
    private static final long TENANT_ID = 0L;

    @Mock
    private SysFileService service;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FileUrlSignService fileUrlSignService;

    @InjectMocks
    private SysFileController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(controller, "fileUrlSignService", fileUrlSignService);
        when(service.currentOwnerId()).thenReturn(OWNER_ID);
    }

    private static SysFile file(Long id, Long createBy, Long tenantId) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setUrl(id + "/report.xlsx");
        file.setFileName("report.xlsx");
        file.setSize(3L);
        file.setMime("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        file.setCreateBy(createBy);
        file.setTenantId(tenantId);
        return file;
    }

    private static HttpServletResponse response(ByteArrayOutputStream sink) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream out = new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) {
                sink.write(b);
            }
        };
        when(response.getOutputStream()).thenReturn(out);
        return response;
    }

    @Test
    @DisplayName("下载本人文件：按归属校验放行，并流式输出字节")
    void downloadOwnFileIsAllowed() throws Exception {
        when(service.getOwnedFile(9L, OWNER_ID)).thenReturn(file(9L, OWNER_ID, TENANT_ID));
        when(fileStorageService.open("9/report.xlsx"))
                .thenReturn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        controller.download(9L, response(sink));

        assertEquals(3, sink.size());
    }

    @Test
    @DisplayName("下载他人文件：与「文件不存在」同一提示，且不读存储字节")
    void downloadOtherUsersFileIsDenied() {
        // 服务层归属校验不通过时返回 null（越权与不存在同表现）
        when(service.getOwnedFile(9L, OWNER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(9L, mock(HttpServletResponse.class)));

        assertEquals(FileUrlSignUtil.INVALID_MESSAGE, ex.getMessage());
        assertEquals(400, ex.getCode(), "失败口径必须是 HTTP 200 + 业务码 400（前端 blob 分支依赖此口径）");
        verify(fileStorageService, never()).open(anyString());
    }

    @Test
    @DisplayName("跨租户文件：即便上传者是自己也看不到（租户维度不豁免）")
    void crossTenantFileIsDenied() {
        when(service.getOwnedFile(9L, OWNER_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> controller.download(9L, mock(HttpServletResponse.class)));
    }

    @Test
    @DisplayName("超管：豁免上传者维度（ownerId=null）")
    void superAdminBypassesOwnerButNotTenant() throws Exception {
        when(service.currentOwnerId()).thenReturn(null);
        when(service.getOwnedFile(9L, null)).thenReturn(file(9L, OTHER_ID, TENANT_ID));
        when(fileStorageService.open("9/report.xlsx"))
                .thenReturn(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        controller.download(9L, response(sink));

        assertEquals(3, sink.size());
    }

    @Test
    @DisplayName("存储读取失败：统一提示，不泄漏底层异常细节")
    void storageFailureIsMasked() throws Exception {
        when(service.getOwnedFile(9L, OWNER_ID)).thenReturn(file(9L, OWNER_ID, TENANT_ID));
        when(fileStorageService.open("9/report.xlsx")).thenThrow(new RuntimeException("connection reset"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(9L, mock(HttpServletResponse.class)));

        assertEquals(FileUrlSignUtil.INVALID_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("删除他人文件：拒绝，既不删存储也不删记录")
    void deleteOtherUsersFileIsDenied() {
        when(service.getOwnedFile(9L, OWNER_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> controller.delete(9L));

        verify(fileStorageService, never()).delete(anyString());
        verify(service, never()).removeById(9L);
    }

    @Test
    @DisplayName("批量删除混入他人 id：只删本人的，他人的静默跳过")
    void batchDeleteSkipsForeignFiles() {
        when(service.getOwnedFile(1L, OWNER_ID)).thenReturn(file(1L, OWNER_ID, TENANT_ID));
        when(service.getOwnedFile(2L, OWNER_ID)).thenReturn(null);

        controller.deleteBatch(Arrays.asList(1L, 2L));

        verify(fileStorageService).delete("1/report.xlsx");
        verify(service).removeById(1L);
        verify(fileStorageService, never()).delete("2/report.xlsx");
        verify(service, never()).removeById(2L);
    }

    @Test
    @DisplayName("分页：按当前归属过滤，出参是签名 URL 而不是裸存储路径")
    void pageIsScopedAndSigned() {
        SysFileQueryRequest query = new SysFileQueryRequest();
        PageRes<SysFile> pageRes = new PageRes<>(1L, 1L, List.of(file(9L, OWNER_ID, TENANT_ID)));
        when(service.page(query, OWNER_ID)).thenReturn(pageRes);
        when(fileUrlSignService.signAttachments(List.of(9L)))
                .thenReturn(java.util.Map.of(9L, "/sys/file/view/9?ticket=abc"));

        var result = controller.page(query);

        verify(service).page(query, OWNER_ID);
        assertEquals("/sys/file/view/9?ticket=abc", result.getData().getList().get(0).getUrl());
    }
}
