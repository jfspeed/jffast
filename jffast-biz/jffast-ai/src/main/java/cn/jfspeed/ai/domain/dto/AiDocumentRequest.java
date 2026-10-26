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
package cn.jfspeed.ai.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库文档新增/修改请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入。
 *
 * @author jffast
 */
@Data
@Schema(description = "知识库文档保存请求")
public class AiDocumentRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @NotBlank(message = "文档标题不能为空")
    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "内容类型")
    private String contentType;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "来源地址")
    private String sourceUrl;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "分片数量")
    private Integer chunkCount;

    @Schema(description = "token 数量")
    private Integer tokenCount;

    @Schema(description = "状态")
    private Integer status;
}
