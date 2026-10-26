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

import java.util.ArrayList;
import java.util.List;

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
@TableName("sys_dict")
public class SysDict extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("parent_id")
    private Long parentId;

    @TableField("name")
    private String name;

    @TableField("code")
    private String code;

    @TableField("value")
    private String value;

    @TableField("sort")
    private Integer sort;

    @TableField("is_type")
    private Integer isType;

    @TableField("remark")
    private String remark;

    public transient List<SysDict> children = new ArrayList<>();

    /**
     * 是否为「字典类型」节点。`is_type` 列 DDL 允许 NULL，存量空值行直接
     * {@code getIsType() == 1} 会拆箱 NPE 并中断缓存清理，故统一走本方法。
     */
    public static boolean isDictType(SysDict dict) {
        return dict != null && Integer.valueOf(1).equals(dict.getIsType());
    }
}
