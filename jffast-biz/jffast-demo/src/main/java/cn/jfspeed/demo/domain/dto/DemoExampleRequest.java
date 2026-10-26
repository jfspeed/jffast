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
package cn.jfspeed.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 示例新增/修改请求体。
 * 与实体分离：不暴露 createBy / updateTime 等审计与派生字段。
 *
 * @author jffast
 */
@Data
@Schema(description = "示例保存请求")
public class DemoExampleRequest {

    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "评分")
    private BigDecimal score;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "价格")
    private BigDecimal price;

    @Schema(description = "是否推荐：0-否，1-是")
    private Integer isRecommend;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "标签（逗号分隔）")
    private String tags;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "链接地址")
    private String url;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "图片")
    private String image;

    @Schema(description = "发布时间")
    private LocalDateTime publishTime;

    @Schema(description = "开始日期")
    private LocalDate startDate;

    @Schema(description = "结束日期")
    private LocalDate endDate;

    @Schema(description = "备注")
    private String remark;
}
