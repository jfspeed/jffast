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
 * AI 对话
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
@Schema(description = "AI 对话")
public class AiConversation extends BaseEntity {

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "对话标题")
    private String title;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "状态：0-禁用 1-启用")
    private Integer status;
}