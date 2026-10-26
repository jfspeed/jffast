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

import java.util.List;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;

import cn.jfspeed.common.data.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dept")
public class SysDept extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("dept_name")
    private String deptName;

    @TableField("level")
    private Integer level;

    /**
     * 备注。
     * <p>
     * 此前 {@code SysDeptRequest} 有 remark、但实体与 {@code sys_dept} 表都没有这一列 ⇒
     * 前端提交的备注在 {@code BeanUtil.copyProperties} 后被静默丢弃，用户填了等于没填。
     * 与 {@code sys_role.remark} 同口径补齐。
     */
    @TableField("remark")
    private String remark;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public transient List<SysDept> children;
}
