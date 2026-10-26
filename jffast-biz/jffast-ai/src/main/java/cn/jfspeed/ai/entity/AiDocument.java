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
package cn.jfspeed.ai.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 文档
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_document")
@Schema(description = "AI 文档")
public class AiDocument extends BaseEntity {

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "文档标题")
    private String title;

    @Schema(description = "文档内容")
    private String content;

    @Schema(description = "内容类型：text/markdown/html/pdf")
    private String contentType;

    @Schema(description = "来源类型：manual/file/url/api")
    private String sourceType;

    @Schema(description = "来源URL")
    private String sourceUrl;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "分块数量")
    private Integer chunkCount;

    @Schema(description = "Token 数量")
    private Integer tokenCount;

    @Schema(description = "状态：0-待处理 1-已处理 2-处理失败")
    private Integer status;
}