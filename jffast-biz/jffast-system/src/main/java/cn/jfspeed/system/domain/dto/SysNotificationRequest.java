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
package cn.jfspeed.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知新增/修改请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入造成越权。
 *
 * @author jffast
 */
@Data
@Schema(description = "通知保存请求")
public class SysNotificationRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "通知标题不能为空")
    @Schema(description = "通知标题")
    private String title;

    @Schema(description = "通知内容")
    private String content;

    @Schema(description = "通知类型：system-系统通知，biz-业务通知")
    private String type;

    @Schema(description = "通知级别：info / warning / error")
    private String level;

    @Schema(description = "发送人")
    private String sender;

    @Schema(description = "接收人类型：all-全体，role-角色，dept-部门，user-用户")
    private String recipientType;

    @Schema(description = "接收人ID列表（逗号分隔）")
    private String recipientIds;

    @Schema(description = "状态：0-未发送，1-已发送")
    private Integer status;

    @Schema(description = "发送时间")
    private LocalDateTime sendTime;

    /** 非表字段：仅在发送请求中携带，不落库 */
    @Schema(description = "发送通道（逗号分隔，如 email,sms；站内信始终发送）")
    private String channels;
}
