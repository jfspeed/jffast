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
 * AI 消息
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
@Schema(description = "AI 消息")
public class AiMessage extends BaseEntity {

    @Schema(description = "对话ID")
    private Long conversationId;

    @Schema(description = "角色：user/assistant/system")
    private String role;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "Token 数量")
    private Integer tokenCount;

    @Schema(description = "元数据")
    private String metadata;
}