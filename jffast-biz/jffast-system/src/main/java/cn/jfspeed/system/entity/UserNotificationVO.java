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
package cn.jfspeed.system.entity;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * @author jffast
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(title = "用户通知视图")
public class UserNotificationVO {

    @Schema(title = "通知ID")
    private Long id;

    @Schema(title = "通知标题")
    private String title;

    @Schema(title = "通知内容")
    private String content;

    @Schema(title = "通知类型(system-系统通知,biz-业务通知)")
    private String type;

    @Schema(title = "通知级别(info/warning/error)")
    private String level;

    @Schema(title = "发送人")
    private String sender;

    @Schema(title = "接收人类型")
    private String recipientType;

    @Schema(title = "是否已读(0-未读,1-已读)")
    private Integer isRead;

    @Schema(title = "发送时间")
    private LocalDateTime sendTime;

    @Schema(title = "创建时间")
    private LocalDateTime createTime;
}