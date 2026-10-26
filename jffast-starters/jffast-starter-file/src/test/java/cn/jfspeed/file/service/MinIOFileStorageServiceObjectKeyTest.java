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

import cn.jfspeed.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * MinIO 对象路径解析用例（删除/下载共用同一解析入口）。
 *
 * @author jffast
 */
class MinIOFileStorageServiceObjectKeyTest {

    private static final String ENDPOINT = "http://127.0.0.1:9000";

    @Test
    @DisplayName("标准 /{bucket}/{key} 路径应解析出 bucket 与 key")
    void shouldParseStandardPath() {
        MinIOFileStorageService.ObjectKey key =
                MinIOFileStorageService.parseObjectKey("/jffast/20261018-120000/a.txt", ENDPOINT);
        assertEquals("jffast", key.bucket());
        assertEquals("20261018-120000/a.txt", key.key());
    }

    @Test
    @DisplayName("带 endpoint 前缀的完整 URL 应剥离前缀")
    void shouldStripEndpointPrefix() {
        MinIOFileStorageService.ObjectKey key = MinIOFileStorageService.parseObjectKey(
                ENDPOINT + "/jffast/docs/a.txt", ENDPOINT);
        assertEquals("jffast", key.bucket());
        assertEquals("docs/a.txt", key.key());
    }

    @Test
    @DisplayName("endpoint 带尾斜杠时仍应正确剥离")
    void shouldStripEndpointWithTrailingSlash() {
        MinIOFileStorageService.ObjectKey key = MinIOFileStorageService.parseObjectKey(
                ENDPOINT + "/jffast/a.txt", ENDPOINT + "/");
        assertEquals("jffast", key.bucket());
        assertEquals("a.txt", key.key());
    }

    @Test
    @DisplayName("缺少前导斜杠的历史值应兼容")
    void shouldAcceptPathWithoutLeadingSlash() {
        MinIOFileStorageService.ObjectKey key =
                MinIOFileStorageService.parseObjectKey("jffast/a.txt", ENDPOINT);
        assertEquals("jffast", key.bucket());
        assertEquals("a.txt", key.key());
    }

    @Test
    @DisplayName("query 与 fragment 应被剥离")
    void shouldStripQueryAndFragment() {
        MinIOFileStorageService.ObjectKey key = MinIOFileStorageService.parseObjectKey(
                "/jffast/a.txt?X-Amz-Signature=abc#part", ENDPOINT);
        assertEquals("jffast", key.bucket());
        assertEquals("a.txt", key.key());
    }

    @Test
    @DisplayName("空路径应拒绝")
    void shouldRejectBlankPath() {
        assertThrows(BusinessException.class,
                () -> MinIOFileStorageService.parseObjectKey("  ", ENDPOINT));
    }

    @Test
    @DisplayName("缺少 object key 的路径应拒绝")
    void shouldRejectPathWithoutObjectKey() {
        assertThrows(BusinessException.class,
                () -> MinIOFileStorageService.parseObjectKey("/jffast", ENDPOINT));
        assertThrows(BusinessException.class,
                () -> MinIOFileStorageService.parseObjectKey("/jffast/", ENDPOINT));
    }

    @Test
    @DisplayName("object key 含 ../ 穿越应拒绝")
    void shouldRejectTraversalKey() {
        assertThrows(BusinessException.class,
                () -> MinIOFileStorageService.parseObjectKey("/jffast/../other/a.txt", ENDPOINT));
    }
}
