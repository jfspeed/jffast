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
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * @author jffast
 */
public interface FileStorageService {

    String uploadFile(String prefix, String filename, MultipartFile file);

    String uploadImgFile(String prefix, String filename, MultipartFile file);

    String uploadHtmlFile(String prefix, String filename, InputStream inputStream);

    void delete(String pathUrl);

    byte[] downloadFile(String pathUrl);

    /**
     * 打开文件流（受控下载 / 签名直显用）。
     *
     * <p>调用方负责关闭返回的流。默认实现不支持流式读取，各存储实现需覆盖。
     *
     * @param pathUrl 存储返回的内部定位键
     */
    default InputStream open(String pathUrl) {
        throw new BusinessException("当前存储实现不支持流式读取");
    }

    /**
     * 上传并登记 {@code sys_file}，返回「URL + 文件 ID」。
     *
     * <p>默认实现只上传不登记（{@code fileId} 为 {@code null}），供没有登记能力的实现类使用；
     * 带登记能力的实现（见 {@code RecordingFileStorageService}）覆盖本方法：
     * 上传 → 登记 → 返回文件 ID，任一环节失败则删除已落盘文件并上抛。
     *
     * @param prefix   业务前缀（写 sys_file.biz_type）
     * @param filename 原始文件名
     * @param file     上传的文件
     * @param image    是否按图片处理（走 {@code uploadImgFile} 的类型校验）
     */
    default FileUploadResult uploadFileWithRecord(String prefix, String filename, MultipartFile file, boolean image) {
        String url = image ? uploadImgFile(prefix, filename, file) : uploadFile(prefix, filename, file);
        return new FileUploadResult(url, null);
    }
}