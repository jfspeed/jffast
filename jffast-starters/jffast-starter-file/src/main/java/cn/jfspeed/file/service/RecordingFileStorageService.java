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

import cn.jfspeed.file.record.SysFileRecorder;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 带登记的 {@link FileStorageService} 装饰器：上传成功后写 {@code sys_file}，
 * 删除后清对应记录，供「文件管理」页面统一查看/下载/删除。
 *
 * <p>{@code recorder} 可为 {@code null}（未接数据库 / 无 spring-jdbc），
 * 此时本装饰器只是透传。
 *
 * @author jffast
 */
public class RecordingFileStorageService implements FileStorageService {

    private final FileStorageService delegate;
    private final SysFileRecorder recorder;

    public RecordingFileStorageService(FileStorageService delegate, SysFileRecorder recorder) {
        this.delegate = delegate;
        this.recorder = recorder;
    }

    @Override
    public String uploadFile(String prefix, String filename, MultipartFile file) {
        String url = delegate.uploadFile(prefix, filename, file);
        if (recorder != null) {
            recorder.record(prefix, filename, url, file.getSize(), file.getContentType());
        }
        return url;
    }

    @Override
    public String uploadImgFile(String prefix, String filename, MultipartFile file) {
        String url = delegate.uploadImgFile(prefix, filename, file);
        if (recorder != null) {
            recorder.record(prefix, filename, url, file.getSize(), file.getContentType());
        }
        return url;
    }

    @Override
    public String uploadHtmlFile(String prefix, String filename, InputStream inputStream) {
        String url = delegate.uploadHtmlFile(prefix, filename, inputStream);
        if (recorder != null) {
            recorder.record(prefix, filename, url, null, "text/html");
        }
        return url;
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
}
