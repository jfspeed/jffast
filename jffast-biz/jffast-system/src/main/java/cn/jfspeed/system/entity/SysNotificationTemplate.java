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

/**
 * 通知模板实体
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notification_template")
@Schema(description = "通知模板")
public class SysNotificationTemplate extends BaseEntity {

    @TableField("group_id")
    @Schema(description = "分组ID")
    private Long groupId;

    @TableField("name")
    @Schema(description = "模板名称")
    private String name;

    @TableField("code")
    @Schema(description = "模板编码")
    private String code;

    @TableField("title_template")
    @Schema(description = "标题模板")
    private String titleTemplate;

    @TableField("content_template")
    @Schema(description = "内容模板")
    private String contentTemplate;

    @TableField("type")
    @Schema(description = "通知类型")
    private String type;

    @TableField("level")
    @Schema(description = "通知级别")
    private String level;

    @TableField("recipient_type")
    @Schema(description = "默认接收人类型")
    private String recipientType;

    @TableField("status")
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;
}
