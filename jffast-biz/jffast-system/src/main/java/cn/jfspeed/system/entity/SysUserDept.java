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
import lombok.Data;

/**
 * 用户部门关联
 *
 * @author jffast
 */
@Data
@TableName("sys_user_dept")
public class SysUserDept {

    // 联合主键字段1：user_id，用@TableId标识
    @TableField(value = "user_id")  // INPUT表示由业务传入*
    private Long userId;

    // 联合主键字段2：dept_id，同样用@TableId标识（关键！*
    @TableField(value = "dept_id")  // 必须设为主键
    private Long deptId;

    public SysUserDept() {}

    public SysUserDept(Long userId, Long deptId) {
        this.userId = userId;
        this.deptId = deptId;
    }
}
