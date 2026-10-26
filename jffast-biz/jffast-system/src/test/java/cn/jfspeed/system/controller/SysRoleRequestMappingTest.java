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
package cn.jfspeed.system.controller;

import com.baomidou.mybatisplus.annotation.TableField;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 防回归：SysRoleRequest → SysRole 的字段搬运契约。
 *
 * 历史缺陷（2026-10 实测暴露）：SysRole.menuIds/deptIds 原以 transient 声明（为让
 * MyBatis-Plus 不映射为表列），而 Hutool BeanUtil.copyProperties 会跳过 transient 字段，
 * 于是角色创建/编辑时 menuIds 恒为 null → sys_role_menu 无绑定 → 非超管用户全 403。
 *
 * @author jffast
 */
class SysRoleRequestMappingTest {

    @Test
    @DisplayName("BeanUtil 拷贝必须把 menuIds/deptIds 搬进实体（曾被 transient 静默吞掉）")
    void copyRequestToEntityKeepsBindingFields() {
        SysRoleRequest req = new SysRoleRequest();
        req.setRoleKey("test_role");
        req.setRoleName("测试角色");
        req.setMenuIds(new Long[]{1001L, 1101L});
        req.setDeptIds(new Long[]{2001L});

        SysRole role = BeanUtil.copyProperties(req, SysRole.class);

        assertArrayEquals(new Long[]{1001L, 1101L}, role.getMenuIds(),
                "menuIds 丢失：检查字段是否又被声明为 transient");
        assertArrayEquals(new Long[]{2001L}, role.getDeptIds(),
                "deptIds 丢失：检查字段是否又被声明为 transient");
    }

    @Test
    @DisplayName("menuIds/deptIds 必须以 @TableField(exist=false) 声明为非表字段")
    void bindingFieldsAreNotTableColumns() throws Exception {
        for (String name : new String[]{"menuIds", "deptIds"}) {
            Field field = SysRole.class.getDeclaredField(name);
            TableField annotation = field.getAnnotation(TableField.class);
            assertNotNull(annotation, name + " 缺少 @TableField 注解");
            assertFalse(annotation.exist(), name + " 不应作为表列映射");
        }
    }
}
