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

/**
 * 租户隔离示例实体。
 * <p>
 * 自带 {@code tenant_id} 列映射（BaseEntity 的 tenantId 为 exist=false）。
 * Base 版租户拦截仅预留（默认关闭），开启后自动按当前租户隔离。
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_tenant_example")
@Schema(description = "租户隔离示例实体")
public class DemoTenantExample extends BaseEntity {

    @TableField("tenant_id")
    @Schema(description = "租户ID（租户拦截自动填充与过滤）")
    private Long tenantId;

    @TableField("title")
    @Schema(description = "标题")
    private String title;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;

    @TableField("status")
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;
}
