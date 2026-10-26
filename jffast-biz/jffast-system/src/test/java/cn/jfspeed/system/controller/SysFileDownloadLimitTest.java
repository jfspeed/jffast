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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;

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
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.SysFileService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件下载上限（P2）：超过配置值时在读取存储前拒绝；0 表示不限制。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysFileDownloadLimitTest {

    /** 下载已加归属校验：这些用例只关心上限，所以让登录人正好是文件登记人 */
    private static final long OWNER_ID = 7L;

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

    private static SysFile file(long size) {
        SysFile file = new SysFile();
        file.setId(1L);
        file.setUrl("2026/10/19/demo.xlsx");
        file.setFileName("demo.xlsx");
        file.setSize(size);
        file.setCreateBy(OWNER_ID);
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
    @DisplayName("超过上限：抛业务异常且不读取存储")
    void overLimitRejectedBeforeStorage() {
        when(service.getById(1L)).thenReturn(file(101));
        ReflectionTestUtils.setField(controller, "downloadMaxBytes", 100L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(1L, mock(HttpServletResponse.class)));

        assertEquals("文件超过下载上限", ex.getMessage());
        verify(fileStorageService, never()).downloadFile("2026/10/19/demo.xlsx");
    }

    @Test
    @DisplayName("上限为 0：不限制，正常输出")
    void zeroLimitMeansUnlimited() throws Exception {
        when(service.getById(1L)).thenReturn(file(Long.MAX_VALUE));
        when(fileStorageService.downloadFile("2026/10/19/demo.xlsx")).thenReturn(new byte[]{1, 2, 3});
        ReflectionTestUtils.setField(controller, "downloadMaxBytes", 0L);
        ByteArrayOutputStream sink = new ByteArrayOutputStream();

        controller.download(1L, response(sink));

        assertEquals(3, sink.size());
        verify(fileStorageService).downloadFile("2026/10/19/demo.xlsx");
    }

    @Test
    @DisplayName("文件不存在：业务异常")
    void missingFileRejected() {
        when(service.getById(2L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.download(2L, mock(HttpServletResponse.class)));

        assertEquals("文件不存在或已清理", ex.getMessage());
    }
}
