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

import java.io.Serializable;

/**
 * 上传结果（URL + 登记后的文件 ID）。
 *
 * <p><b>生成点</b>：{@code SysFileRecorder}（生成 sys_file 主键）→
 * {@code RecordingFileStorageService}（组装本对象）→
 * <b>传递点</b>：{@code FileStorageService#uploadFileWithRecord} 默认方法 →
 * <b>消费点</b>：三个上传 Controller（构造 {@code file:{id}} 引用并换发签名 URL）。
 *
 * <p>{@code fileId} 为 {@code null} 表示当前部署未启用上传登记
 * （{@code jffast.file.record.required=false} 的显式降级态），此时只有 URL 可用。
 *
 * @author jffast
 */
public class FileUploadResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 存储返回的内部定位键（{@code /upload/...} 形态），**禁止直接对外直出** */
    private String url;

    /** sys_file 主键（雪花 ID）；未启用登记时为 null */
    private Long fileId;

    public FileUploadResult() {
    }

    public FileUploadResult(String url, Long fileId) {
        this.url = url;
        this.fileId = fileId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }
}
