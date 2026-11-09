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
import cn.jfspeed.common.file.FileUploadResult;
import cn.jfspeed.file.record.SysFileRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 上传登记装饰器用例（AC-04）。
 *
 * <p>核心不变量：<b>上传与登记必须同生共死</b>。
 * 登记失败时刚落盘的文件必须被删掉——否则磁盘上留下一堆既不在文件管理页可见、
 * 又随时可能被当垃圾清理的孤儿文件，而且没有任何痕迹能追溯到它们。
 *
 * @author jffast
 */
class RecordingFileStorageServiceTest {

    private static final String URL = "/upload/2026/11/a.png";
    private static final long FILE_ID = 1937468592845169665L;

    private static MultipartFile file() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(12L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("a.png");
        return file;
    }

    @Test
    @DisplayName("登记成功：返回 URL + 文件 ID")
    void uploadSucceedsAndReturnsFileId() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.record(eq("user-avatar"), eq("a.png"), eq(URL), any(), any())).thenReturn(FILE_ID);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder);
        String url = service.uploadFile("user-avatar", "a.png", file());

        assertEquals(URL, url);
    }

    @Test
    @DisplayName("uploadFileWithRecord：返回 URL 与文件 ID（出参三字段的来源）")
    void uploadWithRecordReturnsUrlAndFileId() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadImgFile(anyString(), anyString(), any())).thenReturn(URL);
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.record(eq("user-avatar"), eq("a.png"), eq(URL), any(), any())).thenReturn(FILE_ID);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder);
        FileUploadResult result = service.uploadFileWithRecord("user-avatar", "a.png", file(), true);

        assertEquals(URL, result.getUrl());
        assertEquals(FILE_ID, result.getFileId());
        verify(delegate).uploadImgFile(eq("user-avatar"), eq("a.png"), any());
    }

    @Test
    @DisplayName("登记失败：删除已落盘文件并上抛（不留孤儿）")
    void recordFailureCompensatesDelete() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.record(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new BusinessException("上传登记失败，请重试"));

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder);

        assertThrows(BusinessException.class,
                () -> service.uploadFile("user-avatar", "a.png", file()));

        verify(delegate).delete(URL);
    }

    @Test
    @DisplayName("补偿删除自身失败：仍抛原始异常（登记失败才是主因）")
    void compensationDeleteFailureKeepsOriginalCause() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);
        doThrow(new BusinessException("删除文件失败")).when(delegate).delete(URL);
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.record(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new BusinessException("上传登记失败，请重试"));

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadFile("user-avatar", "a.png", file()));
        assertEquals("上传登记失败，请重试", ex.getMessage());
    }

    @Test
    @DisplayName("强制登记但没有登记能力：删除文件并报错，不允许「上传成功但无归属」")
    void requiredWithoutRecorderFailsClosed() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, null, true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.uploadFile("user-avatar", "a.png", file()));
        assertEquals("上传登记能力未启用，请检查数据源与 jffast.file.record.required 配置", ex.getMessage());
        verify(delegate).delete(URL);
    }

    @Test
    @DisplayName("显式降级（required=false）且无登记能力：上传成功，文件 ID 为 null")
    void degradedModeAllowsUploadWithoutRecord() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, null, false);
        String url = service.uploadFile("user-avatar", "a.png", file());

        assertEquals(URL, url);
        verify(delegate, never()).delete(anyString());
    }

    @Test
    @DisplayName("显式降级（required=false）且登记失败：只告警，上传仍成功，文件 ID 为 null")
    void degradedModeToleratesRecordFailure() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.record(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new BusinessException("上传登记失败，请重试"));

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder, false);
        String url = service.uploadFile("user-avatar", "a.png", file());

        assertEquals(URL, url);
        verify(delegate, never()).delete(anyString());
    }

    @Test
    @DisplayName("降级态下 uploadFileWithRecord：URL 可用，文件 ID 为 null")
    void degradedModeReturnsNullFileId() {
        FileStorageService delegate = mock(FileStorageService.class);
        when(delegate.uploadFile(anyString(), anyString(), any())).thenReturn(URL);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, null, false);
        FileUploadResult result = service.uploadFileWithRecord("user-avatar", "a.png", file(), false);

        assertEquals(URL, result.getUrl());
        assertNull(result.getFileId());
    }

    @Test
    @DisplayName("删除：先删存储再删记录")
    void deleteRemovesBothStorageAndRecord() {
        FileStorageService delegate = mock(FileStorageService.class);
        SysFileRecorder recorder = mock(SysFileRecorder.class);

        RecordingFileStorageService service = new RecordingFileStorageService(delegate, recorder);
        service.delete(URL);

        verify(delegate).delete(URL);
        verify(recorder).remove(URL);
    }
}
