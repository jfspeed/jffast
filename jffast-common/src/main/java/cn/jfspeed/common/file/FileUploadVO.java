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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 上传出参。
 *
 * <p><b>三个字段的语义是定死的，不能混用</b>：
 * <ul>
 *   <li>{@code fileId}：雪花 ID 的**字符串**形式（19 位超出 JS 安全整数，数字型会丢精度）；</li>
 *   <li>{@code fileRef}：{@code file:{fileId}}，**唯一允许落库的值**；</li>
 *   <li>{@code url}：短期签名 URL，**仅供即时预览，禁止持久化**（有 TTL，落库即过期）。</li>
 * </ul>
 *
 * @author jffast
 */
@Data
@Schema(description = "上传结果")
public class FileUploadVO {

    @Schema(description = "文件ID（字符串形态，避免前端精度丢失）")
    private String fileId;

    @Schema(description = "文件引用（file:{id}），唯一允许落库的值")
    private String fileRef;

    @Schema(description = "短期签名 URL，仅供即时预览，禁止持久化")
    private String url;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mime;
}
