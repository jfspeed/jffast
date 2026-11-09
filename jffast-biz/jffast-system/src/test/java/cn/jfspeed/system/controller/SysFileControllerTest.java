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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.system.service.FileUrlSignService;
import cn.jfspeed.system.service.SysFileService;

/**
 * 签名 URL 换发端点（AC-08）与匿名直显端点（AC-05 / AC-06 / AC-09 / AC-18）。
 *
 * <p>两条不可让步的约束：
 * <ol>
 *   <li>换发必须先过归属校验——否则「知道 ID」就等于「能拿直链」；</li>
 *   <li>匿名直显的失败口径必须统一为「文件不存在或已清理」，不区分
 *       「签名错 / 过期 / 记录不存在 / 租户不符」，否则可以用返回值差异枚举文件是否存在。</li>
 * </ol>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysFileControllerTest {

    private static final long FILE_ID = 1937468592845169665L;
    private static final long OWNER_ID = 7L;

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
    }

    @Test
    @DisplayName("signed-url：本人文件返回签名 URL 与有效期")
    void signedUrlForOwnFile() {
        when(fileUrlSignService.signOwned(FILE_ID))
                .thenReturn("/sys/file/view/" + FILE_ID + "?ticket=abc.def");
        when(fileUrlSignService.ttlSeconds()).thenReturn(900L);

        SysFileController.SignedUrlVO vo = controller.signedUrl(FILE_ID).getData();

        assertEquals("/sys/file/view/" + FILE_ID + "?ticket=abc.def", vo.getUrl());
        assertEquals(900L, vo.getExpiresIn());
        assertTrue(vo.getUrl().startsWith("/sys/file/view/"),
                "签名 URL 必须是 API 根相对路径（部署形态前缀不同，由前端按 baseURL 拼）");
    }

    @Test
    @DisplayName("signed-url：他人文件统一 400，不暴露「存在但无权限」")
    void signedUrlForOtherUsersFile() {
        when(fileUrlSignService.signOwned(FILE_ID))
                .thenThrow(new BusinessException(FileUrlSignUtil.INVALID_MESSAGE));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.signedUrl(FILE_ID));

        assertEquals(FileUrlSignUtil.INVALID_MESSAGE, ex.getMessage());
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("signed-url：绝不返回裸存储定位键")
    void signedUrlNeverLeaksStoragePath() {
        when(fileUrlSignService.signOwned(FILE_ID))
                .thenReturn("/sys/file/view/" + FILE_ID + "?ticket=abc.def");

        SysFileController.SignedUrlVO vo = controller.signedUrl(FILE_ID).getData();

        assertTrue(!vo.getUrl().startsWith("/upload/"),
                "签名 URL 不得是裸存储路径：" + vo.getUrl());
    }
}
