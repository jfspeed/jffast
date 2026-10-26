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
package cn.jfspeed.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysRole;

/**
 * 角色落库撞唯一索引时的异常翻译（无需 MySQL）。
 * <p>
 * 库侧 uk_sys_role_tenant_role_key 是并发的最后一道闸；闸本身由
 * {@code RoleKeyUniquenessDdlTest} 与真实 MySQL 验证，这里钉住的是闸触发后的对外表现：
 * 必须是可读的业务提示，不能是 500 + 索引名/SQL 原文。
 *
 * @author jffast
 */
class SysRoleDuplicateKeyTranslationTest {

    private static SysRole role(Long id) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleKey("common");
        role.setRoleName("普通用户");
        role.setDataScope(4);
        return role;
    }

    @Test
    @DisplayName("createRole 撞唯一索引应翻译为可读提示，且不泄露索引名")
    void createRoleTranslatesDuplicateKey() {
        SysRoleService service = spy(new SysRoleService());
        doThrow(new DuplicateKeyException("Duplicate entry '0-common' for key 'sys_role.uk_sys_role_tenant_role_key'"))
                .when(service).save(any(SysRole.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createRole(role(1L)));

        assertTrue(ex.getMessage().contains("角色标识已被占用"), "实际提示：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("uk_sys_role_tenant_role_key"),
                "唯一索引名不得出现在对外提示里：" + ex.getMessage());
        assertEquals(409, ex.getCode(), "重名是请求与资源状态冲突，应为 409，实际：" + ex.getCode());
    }

    @Test
    @DisplayName("撞主键（角色 ID 冲突）不得误报成「标识已被占用」")
    void primaryKeyConflictHasItsOwnMessage() {
        SysRoleService service = spy(new SysRoleService());
        // 根因里才带索引名是 MySQL 驱动的真实形态，翻译必须沿 cause 链找
        doThrow(new DuplicateKeyException("failed to insert",
                new java.sql.SQLIntegrityConstraintViolationException("Duplicate entry '7' for key 'sys_role.PRIMARY'")))
                .when(service).save(any(SysRole.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createRole(role(7L)));

        assertTrue(ex.getMessage().contains("角色 ID 冲突"), "实际提示：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("标识已被占用"),
                "ID 冲突不能报成标识冲突：" + ex.getMessage());
        assertFalse(ex.getMessage().contains("PRIMARY"), "约束名不得外传：" + ex.getMessage());
    }

    @Test
    @DisplayName("updateRole 改 roleKey 撞唯一索引同样翻译为可读提示")
    void updateRoleTranslatesDuplicateKey() {
        SysRoleService service = spy(new SysRoleService());
        doReturn(role(1L)).when(service).getById(any());
        doThrow(new DuplicateKeyException("Duplicate entry '0-common' for key 'sys_role.uk_sys_role_tenant_role_key'"))
                .when(service).updateById(any(SysRole.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRole(role(1L), false, false));

        assertTrue(ex.getMessage().contains("角色标识已被占用"), "实际提示：" + ex.getMessage());
    }
}
