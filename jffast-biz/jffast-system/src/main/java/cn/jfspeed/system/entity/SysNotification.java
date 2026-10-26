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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notification")
@Schema(title = "系统通知表")
public class SysNotification extends BaseEntity {

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

    @Schema(title = "接收人类型(all-全体,role-角色,dept-部门,user-用户)")
    private String recipientType;

    @Schema(title = "接收人ID列表(逗号分隔)")
    private String recipientIds;

    @Schema(title = "状态(0-未发送,1-已发送)")
    private Integer status;

    @Schema(title = "发送时间")
    private LocalDateTime sendTime;

    /**
     * 发送通道（逗号分隔，如 email,sms；站内信始终发送）。
     * 仅在发送请求中携带，不落库。
     */
    @Schema(title = "发送通道(逗号分隔,如 email,sms;站内信始终发送)")
    @TableField(exist = false)
    private String channels;
}
