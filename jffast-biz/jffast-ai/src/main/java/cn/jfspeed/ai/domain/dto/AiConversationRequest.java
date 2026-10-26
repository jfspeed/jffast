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
import lombok.Data;

/**
 * 会话新增请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入；
 * userId 由服务端取登录态，不由客户端指定。
 *
 * @author jffast
 */
@Data
@Schema(description = "会话保存请求")
public class AiConversationRequest {

    /** ID（新增时留空由服务端生成） */
    @Schema(description = "ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "会话标题")
    private String title;

    @Schema(description = "模型标识")
    private String model;

    @Schema(description = "状态")
    private Integer status;
}
