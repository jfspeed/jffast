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
package cn.jfspeed.security.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.security.annotation.DataPermission;

/**
 * 数据权限片段构建器：注解元数据是异步快照的唯一来源（R-01/N-01）。
 *
 * @author jffast
 */
class DataPermissionSqlBuilderMetadataTest {

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginUtil.class);
    }

    @AfterEach
    void tearDown() {
        login.close();
    }

    public static class AnnotatedTarget {
        @DataPermission(deptField = "d.id", userField = "u.id")
        public void annotated(Map<String, Object> query, int maxRows) {
        }

        public void plain(Map<String, Object> query, int maxRows) {
        }
    }

    private static LoginUser user(Integer deptId, Long selfId) {
        LoginUser user = new LoginUser();
        user.setId(selfId);
        Map<String, Object> dataScope = new HashMap<>();
        dataScope.put(BaseConstant.DATA_SCOPE_DEPT_ID, deptId);
        dataScope.put(BaseConstant.DATA_SCOPE_SELF, selfId);
        user.setDataScope(dataScope);
        return user;
    }

    @Test
    @DisplayName("resolve：按目标方法注解元数据构建片段（含部门与本人条件）")
    void resolveUsesAnnotationMetadata() {
        login.when(LoginUtil::getLoginUser).thenReturn(user(12, 7L));

        String sql = DataPermissionSqlBuilder.resolve(AnnotatedTarget.class, "annotated",
                Map.class, int.class);

        assertEquals("d.id = '12' and u.id = 7", sql);
    }

    @Test
    @DisplayName("resolve：目标方法不存在/缺少注解一律失败，不回退为无数据权限全量查询")
    void resolveFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> DataPermissionSqlBuilder.resolve(AnnotatedTarget.class, "missing",
                        Map.class, int.class));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> DataPermissionSqlBuilder.resolve(AnnotatedTarget.class, "plain",
                        Map.class, int.class));
        assertEquals(true, ex.getMessage().contains("数据权限注解缺失"));
    }

    @Test
    @DisplayName("build：无登录用户/空数据范围返回 null")
    void buildReturnsNullWithoutScope() {
        assertNull(DataPermissionSqlBuilder.build(null, "d.id", "u.id", true));

        LoginUser empty = new LoginUser();
        empty.setDataScope(Map.of());
        assertNull(DataPermissionSqlBuilder.build(empty, "d.id", "u.id", true));
    }

    @Test
    @DisplayName("build：none 标记固定为 1 = 0（fail-closed）")
    void buildNoneScopeFailsClosed() {
        LoginUser user = new LoginUser();
        user.setDataScope(Map.of(BaseConstant.DATA_SCOPE_NONE, true));

        assertEquals("1 = 0", DataPermissionSqlBuilder.build(user, "d.id", "u.id", true));
        assertEquals("1 = 0", DataPermissionSqlBuilder.build(user, "id", null, true));
    }

    @Test
    @DisplayName("build：受限 map 只有 self 且注解只给 deptField 时返回 1 = 0")
    void buildSelfOnlyWithDeptFieldFailsClosed() {
        LoginUser user = new LoginUser();
        user.setDataScope(Map.of(BaseConstant.DATA_SCOPE_SELF, 7L));

        assertEquals("1 = 0", DataPermissionSqlBuilder.build(user, "id", null, true));
    }

    @Test
    @DisplayName("build：自定义部门集合走 FIND_IN_SET，ID 值做数字白名单清洗")
    void buildCustomDeptSanitizesValues() {
        LoginUser user = new LoginUser();
        user.setDataScope(Map.of(BaseConstant.DATA_SCOPE_CUSTOM_ID, "3,4' or 1=1"));

        String sql = DataPermissionSqlBuilder.build(user, "d.id", null, true);

        assertEquals("(FIND_IN_SET(d.id,'3,411'))", sql);
    }

    @Test
    @DisplayName("build：字段名做标识符白名单清洗")
    void buildSanitizesFieldName() {
        LoginUser user = new LoginUser();
        user.setDataScope(Map.of(BaseConstant.DATA_SCOPE_DEPT_ID, 5));

        String sql = DataPermissionSqlBuilder.build(user, "d.id or 1=1", null, true);

        assertEquals("d.idor11 = '5'", sql);
    }

    @Test
    @DisplayName("build：useId=false 的部门名称先转义反斜杠再转义单引号（P3-20）")
    void buildDeptNameEscapesBackslashBeforeQuote() {
        LoginUser user = new LoginUser();
        user.setDataScope(Map.of(BaseConstant.DATA_SCOPE_DEPT_NAME, "a\\b' or 1=1"));

        String sql = DataPermissionSqlBuilder.build(user, "d.name", null, false);

        assertEquals("d.name = 'a\\\\b'' or 1=1'", sql);
    }
}
