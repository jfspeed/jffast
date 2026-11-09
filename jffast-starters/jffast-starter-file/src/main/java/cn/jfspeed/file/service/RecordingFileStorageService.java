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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 带登记的 {@link FileStorageService} 装饰器：上传成功后写 {@code sys_file}，
 * 删除后清对应记录，供「文件管理」页面统一查看/下载/删除。
 *
 * <p><b>登记是 fail-closed 的</b>（{@code jffast.file.record.required=true}，默认）：
 * 登记失败 ⇒ 删除刚落盘的文件 ⇒ 上抛业务异常，绝不留下「有文件无记录」的孤儿
 * （否则文件既不可见也不可管，等于凭空多出一块无人认领的存储）。
 *
 * <p>显式降级（{@code required=false}）时：{@code recorder} 可以为 {@code null}，
 * 此时不登记、上传照常成功，但返回的 {@code fileId} 为 {@code null}，
 * 且**不得**启用受控下载与匿名签名直显（见 {@code SysFileViewController} 的条件装配）。
 *
 * @author jffast
 */
@Slf4j
public class RecordingFileStorageService implements FileStorageService {

    private final FileStorageService delegate;
    private final SysFileRecorder recorder;

    /** 是否强制登记；false = 显式降级（登记失败只告警，不影响上传） */
    private final boolean recordRequired;

    public RecordingFileStorageService(FileStorageService delegate, SysFileRecorder recorder) {
        this(delegate, recorder, true);
    }

    public RecordingFileStorageService(FileStorageService delegate, SysFileRecorder recorder, boolean recordRequired) {
        this.delegate = delegate;
        this.recorder = recorder;
        this.recordRequired = recordRequired;
    }

    @Override
    public String uploadFile(String prefix, String filename, MultipartFile file) {
        String url = delegate.uploadFile(prefix, filename, file);
        recordOrCompensate(url, prefix, filename, file.getSize(), file.getContentType());
        return url;
    }

    @Override
    public String uploadImgFile(String prefix, String filename, MultipartFile file) {
        String url = delegate.uploadImgFile(prefix, filename, file);
        recordOrCompensate(url, prefix, filename, file.getSize(), file.getContentType());
        return url;
    }

    @Override
    public String uploadHtmlFile(String prefix, String filename, InputStream inputStream) {
        String url = delegate.uploadHtmlFile(prefix, filename, inputStream);
        recordOrCompensate(url, prefix, filename, null, "text/html");
        return url;
    }

    /**
     * 上传并登记，返回「URL + 文件 ID」。
     *
     * <p>登记失败时先删除已落盘文件再上抛，避免出现「磁盘有文件、库里无记录」的孤儿。
     */
    @Override
    public FileUploadResult uploadFileWithRecord(String prefix, String filename, MultipartFile file, boolean image) {
        String url = image
                ? delegate.uploadImgFile(prefix, filename, file)
                : delegate.uploadFile(prefix, filename, file);
        Long fileId = recordOrCompensate(url, prefix, filename, file.getSize(), file.getContentType());
        return new FileUploadResult(url, fileId);
    }

    @Override
    public void delete(String pathUrl) {
        delegate.delete(pathUrl);
        if (recorder != null) {
            recorder.remove(pathUrl);
        }
    }

    @Override
    public byte[] downloadFile(String pathUrl) {
        return delegate.downloadFile(pathUrl);
    }

    @Override
    public InputStream open(String pathUrl) {
        return delegate.open(pathUrl);
    }

    /**
     * 登记 {@code sys_file}；失败时补偿删除已落盘文件。
     *
     * @return 文件 ID；降级态（不登记）返回 null
     */
    private Long recordOrCompensate(String url, String prefix, String filename, Long size, String mime) {
        if (recorder == null) {
            if (recordRequired) {
                compensateDelete(url);
                throw new BusinessException("上传登记能力未启用，请检查数据源与 jffast.file.record.required 配置");
            }
            return null;
        }
        try {
            return recorder.record(prefix, filename, url, size, mime);
        } catch (RuntimeException e) {
            if (!recordRequired) {
                // 显式降级态：登记失败只告警，不阻断上传
                log.warn("sys_file 登记失败（已降级，不影响上传结果）: {}", e.getMessage());
                return null;
            }
            compensateDelete(url);
            throw e;
        }
    }

    /** 补偿删除刚落盘的文件；删除本身失败只告警（原始异常才是主因） */
    private void compensateDelete(String url) {
        try {
            delegate.delete(url);
        } catch (Exception e) {
            log.warn("上传登记失败后的补偿删除也失败（可能留下孤儿文件）: {}", url, e);
        }
    }
}
