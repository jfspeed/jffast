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
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色部门关联（当角色数据范围为自定义数据时使用）
 *
 * @author jffast
 */
@Data
@TableName("sys_role_dept")
public class SysRoleDept {

    /**
     * 角色ID
     */
    @TableId("role_id")
    private Long roleId;

    /**
     * 部门ID
     */
    @TableField("dept_id")
    private Long deptId;

    public SysRoleDept() {}

    public SysRoleDept(Long roleId, Long deptId) {
        this.roleId = roleId;
        this.deptId = deptId;
    }
}
