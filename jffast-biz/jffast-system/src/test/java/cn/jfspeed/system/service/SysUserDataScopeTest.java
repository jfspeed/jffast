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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.security.util.DataPermissionSqlBuilder;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;

/**
 * 数据范围语义 F1–F13（设计 v3 §7.2.1 表 A）：map 内容与查询片段双断言。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserDataScopeTest {

    private static final Long USER_ID = 7L;
    private static final Long DEPT_ID = 10L;
    private static final Long CUSTOM_DEPT_ID = 5L;
    private static final String CUSTOM_DEPT_NAME = "研发中心";
    private static final String CHILD_IDS = "10,11";
    private static final String CHILD_NAMES = "技术部,研发部";

    @Mock
    private SysDeptService sysDeptService;

    @InjectMocks
    private SysUserService service;

    @BeforeEach
    void setUp() {
        Map<String, String> child = new HashMap<>();
        child.put("id", CHILD_IDS);
        child.put("name", CHILD_NAMES);
        when(sysDeptService.getChildIdAndName(DEPT_ID)).thenReturn(child);

        SysDept customDept = new SysDept();
        customDept.setId(CUSTOM_DEPT_ID);
        customDept.setDeptName(CUSTOM_DEPT_NAME);
        when(sysDeptService.selectIdListByRoleId(any())).thenReturn(List.of(CUSTOM_DEPT_ID));
        when(sysDeptService.listByIds(any())).thenReturn(List.of(customDept));
    }

    private static SysRole role(Long id, Integer dataScope) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setDataScope(dataScope);
        return role;
    }

    private static SysUser user(Long deptId, String deptName, SysRole... roles) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setDeptId(deptId);
        user.setDeptName(deptName);
        user.setRoleList(new ArrayList<>(Arrays.asList(roles)));
        return user;
    }

    private Map<String, Object> scopeOf(SysUser user) {
        service.setDataScope(user);
        Map<String, Object> dataScope = user.getDataScope();
        assertNotNull(dataScope);
        return dataScope;
    }

    private static LoginUser loginUser(Map<String, Object> dataScope) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(USER_ID);
        loginUser.setDataScope(dataScope);
        return loginUser;
    }

    /** (a) 用户查询：deptField = d.id，userField = u.id */
    private static String userSql(Map<String, Object> dataScope) {
        return DataPermissionSqlBuilder.build(loginUser(dataScope), "d.id", "u.id", true);
    }

    /** (b) 部门查询：仅 deptField = id（SysDeptService.list / SysDeptScopeQuery） */
    private static String deptSql(Map<String, Object> dataScope) {
        return DataPermissionSqlBuilder.build(loginUser(dataScope), "id", null, true);
    }

    private static void assertNone(Map<String, Object> dataScope) {
        assertEquals(Boolean.TRUE, dataScope.get(BaseConstant.DATA_SCOPE_NONE));
        assertEquals("1 = 0", userSql(dataScope));
        assertEquals("1 = 0", deptSql(dataScope));
    }

    @Test
    @DisplayName("F1 scope=1 全部数据：显式空 map，两查询不加条件")
    void f1AllDataUsesEmptyMap() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 1)));

        assertTrue(dataScope.isEmpty());
        assertNull(userSql(dataScope));
        assertNull(deptSql(dataScope));
    }

    @Test
    @DisplayName("F2 无角色 + 有部门：none，两查询 1 = 0")
    void f2NoRolesWithDeptFailsClosed() {
        assertNone(scopeOf(user(DEPT_ID, "技术部")));
    }

    @Test
    @DisplayName("F3 无角色 + 无部门：none，两查询 1 = 0")
    void f3NoRolesWithoutDeptFailsClosed() {
        assertNone(scopeOf(user(null, null)));
    }

    @Test
    @DisplayName("F4 scope=3 本部门：bmid/bmmc")
    void f4DeptScopeWritesDeptId() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 3)));

        assertEquals("10", dataScope.get(BaseConstant.DATA_SCOPE_DEPT_ID));
        assertEquals("技术部", dataScope.get(BaseConstant.DATA_SCOPE_DEPT_NAME));
        assertEquals("d.id = '10'", userSql(dataScope));
        assertEquals("id = '10'", deptSql(dataScope));
    }

    @Test
    @DisplayName("F5 scope=2 本部门及以下：bmjzbmid 含本部门")
    void f5DeptAndChildScopeWritesParentDept() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 2)));

        assertEquals(CHILD_IDS, dataScope.get(BaseConstant.DATA_SCOPE_DEPT_PARENT_ID));
        assertEquals(CHILD_NAMES, dataScope.get(BaseConstant.DATA_SCOPE_DEPT_PARENT_NAME));
        assertFalse(dataScope.containsKey(BaseConstant.DATA_SCOPE_DEPT_ID));
        assertTrue(dataScope.get(BaseConstant.DATA_SCOPE_DEPT_PARENT_ID).toString().contains("10"));
        assertEquals("FIND_IN_SET(d.id,'" + CHILD_IDS + "')", userSql(dataScope));
        assertEquals("FIND_IN_SET(id,'" + CHILD_IDS + "')", deptSql(dataScope));
    }

    @Test
    @DisplayName("F6 scope=4 仅本人 + 有部门：self + bmid")
    void f6SelfScopeWithDeptKeepsDeptDimension() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 4)));

        assertEquals(USER_ID, dataScope.get(BaseConstant.DATA_SCOPE_SELF));
        assertEquals("10", dataScope.get(BaseConstant.DATA_SCOPE_DEPT_ID));
        assertEquals("d.id = '10' and u.id = 7", userSql(dataScope));
        assertEquals("id = '10'", deptSql(dataScope));
    }

    @Test
    @DisplayName("F7 scope=4 无部门：只看自己，部门查询 fail-closed")
    void f7SelfScopeWithoutDeptFailsClosedOnDept() {
        Map<String, Object> dataScope = scopeOf(user(null, null, role(2L, 4)));

        assertEquals(USER_ID, dataScope.get(BaseConstant.DATA_SCOPE_SELF));
        assertEquals("u.id = 7", userSql(dataScope));
        assertFalse(userSql(dataScope).contains("d.id"));
        assertEquals("1 = 0", deptSql(dataScope));
    }

    @Test
    @DisplayName("F8 纯 scope=5 有绑定：仅自定义部门，无 OR 本部门")
    void f8CustomScopeKeepsOnlyCustomDept() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 5)));

        assertEquals("5", dataScope.get(BaseConstant.DATA_SCOPE_CUSTOM_ID));
        assertEquals(CUSTOM_DEPT_NAME, dataScope.get(BaseConstant.DATA_SCOPE_CUSTOM_NAME));
        assertEquals("(FIND_IN_SET(d.id,'5'))", userSql(dataScope));
        assertFalse(userSql(dataScope).contains("or d.id = '10'"));
        assertEquals("(FIND_IN_SET(id,'5'))", deptSql(dataScope));
    }

    @Test
    @DisplayName("F9 scope=5 无绑定 + 有部门：none")
    void f9CustomScopeWithoutBindingFailsClosed() {
        when(sysDeptService.selectIdListByRoleId(any())).thenReturn(List.of());

        assertNone(scopeOf(user(DEPT_ID, "技术部", role(2L, 5))));
    }

    @Test
    @DisplayName("F10 scope=5 无绑定 + 无部门：none")
    void f10CustomScopeWithoutBindingAndDeptFailsClosed() {
        when(sysDeptService.selectIdListByRoleId(any())).thenReturn(List.of());

        assertNone(scopeOf(user(null, null, role(2L, 5))));
    }

    @Test
    @DisplayName("F11 scope=3 + 4 并存：bmid 与 self 同时写入")
    void f11DeptAndSelfScopesCoexist() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 3), role(3L, 4)));

        assertEquals("10", dataScope.get(BaseConstant.DATA_SCOPE_DEPT_ID));
        assertEquals(USER_ID, dataScope.get(BaseConstant.DATA_SCOPE_SELF));
        assertEquals("d.id = '10' and u.id = 7", userSql(dataScope));
        assertEquals("id = '10'", deptSql(dataScope));
    }

    @Test
    @DisplayName("F12 scope=3 无部门：none")
    void f12DeptScopeWithoutDeptFailsClosed() {
        assertNone(scopeOf(user(null, null, role(2L, 3))));
    }

    @Test
    @DisplayName("F13 scope=5 + 3 混合角色：OR 本部门分支有意保留")
    void f13MixedCustomAndDeptKeepsOrBranch() {
        Map<String, Object> dataScope = scopeOf(user(DEPT_ID, "技术部", role(2L, 5), role(3L, 3)));

        assertEquals("5", dataScope.get(BaseConstant.DATA_SCOPE_CUSTOM_ID));
        assertEquals("10", dataScope.get(BaseConstant.DATA_SCOPE_DEPT_ID));
        assertEquals("(FIND_IN_SET(d.id,'5') or d.id = '10')", userSql(dataScope));
        assertEquals("(FIND_IN_SET(id,'5') or id = '10')", deptSql(dataScope));
    }
}
