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
 * 知识库新增/修改请求体。
 * 与实体分离：不接收 createBy / createTime / updateBy / updateTime / isDeleted，
 * 这些字段由服务端自动填充决定，避免客户端伪造写入。
 *
 * @author jffast
 */
@Data
@Schema(description = "知识库保存请求")
public class AiKnowledgeBaseRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "知识库名称不能为空")
    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "知识库编码")
    private String code;

    @Schema(description = "描述")
    private String description;

    /** 仅登记配置：Base 不含大模型调用，该字段供接入方自行实现向量化时使用 */
    @Schema(description = "向量模型标识（仅登记，Base 不含实际向量化调用）")
    private String embeddingModel;

    @Schema(description = "分片大小")
    private Integer chunkSize;

    @Schema(description = "分片重叠大小")
    private Integer chunkOverlap;

    @Schema(description = "状态：0-停用，1-启用")
    private Integer status;
}
