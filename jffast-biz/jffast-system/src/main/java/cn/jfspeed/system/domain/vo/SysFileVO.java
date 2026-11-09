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
package cn.jfspeed.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件记录出参（文件管理页）。
 *
 * <p>⚠️ <b>不回裸 {@code url}</b>：{@code url} 是存储内部定位键（{@code /upload/...}），
 * 一旦直出就等于把「可直读的路径」重新贴到前端——那正是本轮要收口的东西。
 * 出参里的 {@code url} 是**短期签名 URL**（{@code /sys/file/view/{id}?ticket=...}）。
 *
 * @author jffast
 */
@Data
@Schema(description = "文件记录")
public class SysFileVO {

    @Schema(description = "文件ID")
    private Long id;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "MIME 类型")
    private String mime;

    @Schema(description = "业务类型（上传前缀）")
    private String bizType;

    @Schema(description = "存储类型（local/minio/aliyun-oss/tencent-cos）")
    private String storageType;

    @Schema(description = "上传人")
    private Long createBy;

    @Schema(description = "归属租户（0=平台/单租户）")
    private Long tenantId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "短期签名 URL（非裸存储路径）")
    private String url;
}
