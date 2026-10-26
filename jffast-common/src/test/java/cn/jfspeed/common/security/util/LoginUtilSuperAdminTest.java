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
package cn.jfspeed.common.security.util;

import cn.jfspeed.common.core.base.BaseConstant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超管判定语义的回归测试。
 *
 * <p>钉住一条不变量：<b>只有 roleKey = "super_admin" 或内置超管角色 ID（1L）才是超管</b>。
 *
 * <p>背景：{@code LoginUtil.isSuperAdmin()} 曾误用 {@code BaseConstant.SUPER_ADMIN}（值 "admin"，
 * 那是**超管用户名**常量）。而 {@code BaseConstant.ROLE_ADMIN}（同样是 "admin"）是**部门级
 * 管理员**角色的 roleKey —— 两个常量字面量相同，于是持有 roleKey="admin" 角色的普通用户会被
 * 判成超管，跳过请假单归属校验、异步任务归属过滤、角色写入校验（{@code validateRoleWrite}
 * 的 superAdmin 早退）等授权/归属短路开关。本测试确保该回归不会再次发生。
 *
 * @author jffast
 */
class LoginUtilSuperAdminTest {

    @Test
    @DisplayName("roleKey=admin（部门级管理员角色）不得被判定为超级管理员")
    void departmentAdminRoleKeyIsNotSuperAdmin() {
        assertFalse(LoginUtil.isSuperAdminByRole(List.of(BaseConstant.ROLE_ADMIN), List.of(2L)),
                "roleKey=\"admin\" 是部门级管理员语义，绝不能命中超管判定");
    }

    @Test
    @DisplayName("roleKey=super_admin 才是超级管理员")
    void superAdminRoleKeyIsSuperAdmin() {
        assertTrue(LoginUtil.isSuperAdminByRole(List.of(BaseConstant.ROLE_SUPER_ADMIN), List.of(9L)));
    }

    @Test
    @DisplayName("内置超管角色 ID=1L 命中超管（不依赖 roleKey）")
    void superAdminRoleIdIsSuperAdmin() {
        assertTrue(LoginUtil.isSuperAdminByRole(List.of("whatever"), List.of(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG)));
    }

    @Test
    @DisplayName("普通角色既不靠 roleKey 也不靠 roleId 命中")
    void ordinaryRoleIsNotSuperAdmin() {
        assertFalse(LoginUtil.isSuperAdminByRole(List.of(BaseConstant.ROLE_COMMON), List.of(2L)));
    }

    @Test
    @DisplayName("空列表与 null 列表不得抛异常，且判定为非超管")
    void emptyAndNullListsAreSafe() {
        assertFalse(LoginUtil.isSuperAdminByRole(Collections.emptyList(), Collections.emptyList()));
        assertFalse(LoginUtil.isSuperAdminByRole(null, null));
    }

    @Test
    @DisplayName("角色列表里混入 admin 也不能把普通用户提升为超管")
    void mixedRoleListDoesNotEscalate() {
        List<String> codes = Arrays.asList(BaseConstant.ROLE_COMMON, BaseConstant.ROLE_ADMIN);
        assertFalse(LoginUtil.isSuperAdminByRole(codes, List.of(2L, 3L)));
    }
}
