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
package cn.jfspeed.flow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 流程分类实体。
 * <p>
 * 用于组织流程定义（一个分类下可挂多个流程定义），
 * Flowable 的 {@code ProcessDefinition.category} 字段存本表的 {@code categoryCode}。
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_category")
@Schema(description = "流程分类")
public class WfCategory extends BaseEntity {

    @TableField("category_code")
    @Schema(description = "分类编码（如 leave/expense，对应 Flowable process category）")
    private String categoryCode;

    @TableField("category_name")
    @Schema(description = "分类名称")
    private String categoryName;

    @TableField("sort")
    @Schema(description = "排序（asc）")
    private Integer sort;

    @TableField("status")
    @Schema(description = "状态：0=启用，1=禁用")
    private Integer status;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;
}
