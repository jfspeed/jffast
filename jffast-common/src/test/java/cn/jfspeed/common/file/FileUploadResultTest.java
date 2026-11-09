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
package cn.jfspeed.common.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 上传结果与上传出参的契约用例（AC-03）。
 *
 * <p>三个字段的语义是定死的：{@code fileId} 字符串、{@code fileRef} 唯一可落库、
 * {@code url} 仅供预览。混用会把「会过期的签名 URL」落进库里。
 *
 * @author jffast
 */
class FileUploadResultTest {

    @Test
    @DisplayName("登记成功：URL 与文件 ID 一起返回")
    void carriesUrlAndFileId() {
        FileUploadResult result = new FileUploadResult("/upload/2026/11/a.png", 1937468592845169665L);

        assertEquals("/upload/2026/11/a.png", result.getUrl());
        assertEquals(1937468592845169665L, result.getFileId());
    }

    @Test
    @DisplayName("降级态（未启用登记）：文件 ID 为 null，但 URL 仍可用")
    void degradedModeHasNullFileId() {
        FileUploadResult result = new FileUploadResult("/upload/2026/11/a.png", null);

        assertEquals("/upload/2026/11/a.png", result.getUrl());
        assertNull(result.getFileId());
    }

    @Test
    @DisplayName("上传出参：fileId 是字符串，fileRef 形如 file:{id}")
    void uploadVoUsesStringFileIdAndStableRef() {
        FileUploadVO vo = new FileUploadVO();
        Long fileId = 1937468592845169665L;
        vo.setFileId(String.valueOf(fileId));
        vo.setFileRef(FileRefs.of(fileId));
        vo.setUrl("/sys/file/view/" + fileId + "?ticket=abc");

        assertEquals("1937468592845169665", vo.getFileId());
        assertEquals("file:1937468592845169665", vo.getFileRef());
        assertEquals("/sys/file/view/1937468592845169665?ticket=abc", vo.getUrl());
    }

    @Test
    @DisplayName("降级态出参：fileId 与 fileRef 都为空，url 仍可用于预览")
    void uploadVoDegradedHasNoRef() {
        FileUploadVO vo = new FileUploadVO();
        vo.setFileId(null);
        vo.setFileRef(FileRefs.of(null));
        vo.setUrl("/upload/2026/11/a.png");

        assertNull(vo.getFileId());
        assertNull(vo.getFileRef());
        assertEquals("/upload/2026/11/a.png", vo.getUrl());
    }
}
