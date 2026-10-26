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

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

/**
 * 角色菜单关联
 *
 * @author jffast
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu {

    /**
     * 角色ID
     */
    @TableId(value = "role_id",type = IdType.INPUT)
    private Long roleId;

    /**
     * 菜单ID
     */
    private Long menuId;

    public SysRoleMenu(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    public SysRoleMenu() {}
}
