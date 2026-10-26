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
package cn.jfspeed.file.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;

/**
 * 本地文件路径穿越防护用例（P2 整改）。
 *
 * @author jffast
 */
class LocalFileStorageServicePathTest {

    private LocalFileStorageService service;
    private Path baseDir;

    @BeforeEach
    void setUp() throws Exception {
        baseDir = Paths.get(System.getProperty("java.io.tmpdir"), "jffast-file-test")
                .toAbsolutePath().normalize();
        FileStorageProperties props = new FileStorageProperties();
        props.getLocal().setPath(baseDir.toString());
        props.getLocal().setAccessPath("/upload");
        service = new LocalFileStorageService(props);
    }

    @Test
    @DisplayName("delete 应拒绝 ../ 穿越到 base 外")
    void deleteShouldRejectTraversal() {
        assertThrows(BusinessException.class, () -> service.delete("/upload/../escape.txt"));
    }

    @Test
    @DisplayName("download 应拒绝多重 ../ 穿越")
    void downloadShouldRejectTraversal() {
        assertThrows(BusinessException.class, () -> service.downloadFile("/upload/../../etc/passwd"));
    }

    @Test
    @DisplayName("upload 文件名含 .. 或 / 应被拒绝")
    void uploadShouldRejectTraversalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../evil.txt", "text/plain", "x".getBytes());
        assertThrows(BusinessException.class, () -> service.uploadFile("docs", "../evil.txt", file));
        assertThrows(BusinessException.class, () -> service.uploadFile("docs", "a/b.txt", file));
    }

    @Test
    @DisplayName("合法相对路径应解析在 base 内（穿越防护不误伤正常路径）")
    void normalRelativePathStaysUnderBase() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.txt", "text/plain", "hello".getBytes());
        String url = service.uploadFile("docs", "ok.txt", file);
        assertTrue(url.startsWith("/upload/"));
    }
}
