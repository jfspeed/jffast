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
package cn.jfspeed.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_example")
@Schema(description = "示例实体")
public class DemoExample extends BaseEntity {

    @TableField("title")
    @Schema(description = "标题")
    private String title;

    @TableField("content")
    @Schema(description = "内容")
    private String content;

    @TableField("description")
    @Schema(description = "描述")
    private String description;

    @TableField("status")
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @TableField("type")
    @Schema(description = "类型")
    private String type;

    @TableField("priority")
    @Schema(description = "优先级")
    private Integer priority;

    @TableField("score")
    @Schema(description = "评分")
    private BigDecimal score;

    @TableField("quantity")
    @Schema(description = "数量")
    private Integer quantity;

    @TableField("price")
    @Schema(description = "价格")
    private BigDecimal price;

    @TableField("is_recommend")
    @Schema(description = "是否推荐：0-否，1-是")
    private Integer isRecommend;

    @TableField("is_deleted")
    @Schema(description = "逻辑删除：0-未删除，1-已删除")
    private Integer isDeleted;

    @TableField("sort")
    @Schema(description = "排序")
    private Integer sort;

    @TableField("tags")
    @Schema(description = "标签（逗号分隔）")
    private String tags;

    @TableField("email")
    @Schema(description = "邮箱")
    private String email;

    @TableField("phone")
    @Schema(description = "手机号")
    private String phone;

    @TableField("url")
    @Schema(description = "链接地址")
    private String url;

    @TableField("icon")
    @Schema(description = "图标")
    private String icon;

    @TableField("image")
    @Schema(description = "图片")
    private String image;

    @TableField("publish_time")
    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @TableField("start_date")
    @Schema(description = "开始日期")
    private LocalDate startDate;

    @TableField("end_date")
    @Schema(description = "结束日期")
    private LocalDate endDate;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;
}
