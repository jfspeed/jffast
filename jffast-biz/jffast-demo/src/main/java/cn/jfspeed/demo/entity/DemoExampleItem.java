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

/**
 * 示例明细行：演示「主表 + 明细子表」的保存与回显。
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_example_item")
@Schema(description = "示例明细行")
public class DemoExampleItem extends BaseEntity {

    @TableField("example_id")
    @Schema(description = "主表ID")
    private Long exampleId;

    @TableField("product_id")
    @Schema(description = "物料ID")
    private Long productId;

    @TableField("product_name")
    @Schema(description = "物料名称")
    private String productName;

    @TableField("qty")
    @Schema(description = "数量")
    private Integer qty;

    @TableField("price")
    @Schema(description = "单价")
    private BigDecimal price;

    @TableField("sort")
    @Schema(description = "排序")
    private Integer sort;
}
