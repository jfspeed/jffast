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
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("role_key")
    private String roleKey;

    @TableField("role_name")
    private String roleName;

    @TableField("is_open")
    private Boolean isOpen;

    @TableField("data_scope")
    private Integer dataScope;

    @TableField("remark")
    private String remark;

    /**
     * 菜单ID集合（非表字段）。注意：**不要**改成 transient——Hutool BeanUtil.copyProperties
     * （SysRoleController 里 SysRoleRequest→SysRole 的转换）会跳过 transient 字段，
     * 导致 menuIds 静默丢失、角色菜单绑定失效（表现为租户/普通角色用户全 403）。
     */
    @TableField(exist = false)
    private Long[] menuIds;

    /**
     * 部门ID集合（非表字段，同上，勿改 transient）
     */
    @TableField(exist = false)
    private Long[] deptIds;
}
