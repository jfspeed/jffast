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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SysDeptRequest;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysDeptService;
import cn.jfspeed.system.service.SysUserScopeQuery;
import cn.jfspeed.system.service.SysUserService;

/**
 * 部门域数据范围：detail / update / create / delete 必须接 {@link SysDeptScopeQuery}，
 * 同时保证同部门内的合法维护不被误伤。
 *
 * <p>复现条件（线上实测）：data_scope=3（本部门）的用户属于 701，却可以
 * GET /sys/dept/detail/702 看到 702 全量信息，并 PUT /sys/dept/update 改组名。</p>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDeptScopeAuthorizationTest {

    private static final Long OWN_DEPT = 701L;
    private static final Long FOREIGN_DEPT = 702L;
    private static final Long PARENT_DEPT = 700L;

    @Mock
    private SysDeptService service;
    @Mock
    private SysUserService sysUserService;
    @Mock
    private SysDeptScopeQuery deptScopeQuery;
    @Mock
    private SysUserScopeQuery userScopeQuery;

    private SysDeptController controller;

    @BeforeEach
    void setUp() {
        controller = new SysDeptController();
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(controller, "deptScopeQuery", deptScopeQuery);
        ReflectionTestUtils.setField(controller, "userScopeQuery", userScopeQuery);
    }

    private static SysDept dept(Long id, Long parentId, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        dept.setLevel(2);
        return dept;
    }

    private static SysDeptRequest request(Long id, Long parentId, String name) {
        SysDeptRequest req = new SysDeptRequest();
        req.setId(id);
        req.setParentId(parentId);
        req.setDeptName(name);
        return req;
    }

    @Test
    @DisplayName("detail：范围外部门 → 403，不返回实体")
    void detailOutOfScopeRejected() {
        when(deptScopeQuery.findVisibleDept(FOREIGN_DEPT)).thenReturn(null);

        R result = controller.getById(FOREIGN_DEPT);

        assertEquals(403, result.getCode());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("detail：范围内部门 → 200 正常返回")
    void detailInScopeSucceeds() {
        SysDept own = dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA");
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(own);

        R result = controller.getById(OWN_DEPT);

        assertEquals(200, result.getCode());
        assertEquals(own, result.getData());
    }

    @Test
    @DisplayName("update：范围外部门 → 403 且不落库")
    void updateOutOfScopeRejected() {
        when(deptScopeQuery.findVisibleDept(FOREIGN_DEPT)).thenReturn(null);
        when(deptScopeQuery.findVisibleDept(PARENT_DEPT)).thenReturn(dept(PARENT_DEPT, 0L, "AuditRoot"));

        R result = controller.update(request(FOREIGN_DEPT, PARENT_DEPT, "Hacked"));

        assertEquals(403, result.getCode());
        verify(service, never()).updateDept(any());
    }

    @Test
    @DisplayName("update：同部门合法改名 → 200 且落库")
    void updateOwnDeptSucceeds() {
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));
        when(deptScopeQuery.findVisibleDept(PARENT_DEPT)).thenReturn(dept(PARENT_DEPT, 0L, "AuditRoot"));
        when(service.checkDeptName(any())).thenReturn(false);

        R result = controller.update(request(OWN_DEPT, PARENT_DEPT, "AuditDeptA-Renamed"));

        assertEquals(200, result.getCode());
        verify(service).updateDept(any());
    }

    @Test
    @DisplayName("update：上级不变（上级在范围外不可见）→ 仍 200，合法改名不被误伤")
    void updateOwnDeptWithInvisibleUnchangedParentSucceeds() {
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));
        // data_scope=本部门时，上级天然不在自己的数据范围内
        when(deptScopeQuery.findVisibleDept(PARENT_DEPT)).thenReturn(null);
        when(service.checkDeptName(any())).thenReturn(false);

        R result = controller.update(request(OWN_DEPT, PARENT_DEPT, "AuditDeptA-Renamed"));

        assertEquals(200, result.getCode());
        verify(service).updateDept(any());
    }

    @Test
    @DisplayName("update：非超管把部门挂到根级 → 403")
    void updateMoveToRootByNonSuperRejected() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::isSuperAdmin).thenReturn(false);
            when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));

            R result = controller.update(request(OWN_DEPT, 0L, "AuditDeptA"));

            assertEquals(403, result.getCode());
            verify(service, never()).updateDept(any());
        }
    }

    @Test
    @DisplayName("update：把部门挂到范围外父级 → 403 且不落库")
    void updateWithForeignParentRejected() {
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));
        when(deptScopeQuery.findVisibleDept(FOREIGN_DEPT)).thenReturn(null);

        R result = controller.update(request(OWN_DEPT, FOREIGN_DEPT, "AuditDeptA"));

        assertEquals(403, result.getCode());
        verify(service, never()).updateDept(any());
    }

    @Test
    @DisplayName("create：挂到范围外父级 → 403 且不落库")
    void createUnderForeignParentRejected() {
        when(deptScopeQuery.findVisibleDept(FOREIGN_DEPT)).thenReturn(null);

        R result = controller.insert(request(null, FOREIGN_DEPT, "AuditChild"));

        assertEquals(403, result.getCode());
        verify(service, never()).createDept(any());
        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("create：挂在自己部门下 → 200 且落库")
    void createUnderOwnParentSucceeds() {
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));
        when(service.checkDeptName(any())).thenReturn(false);

        R result = controller.insert(request(null, OWN_DEPT, "AuditChild"));

        assertEquals(200, result.getCode());
        verify(service).createDept(any());
        verify(service).save(any());
    }

    @Test
    @DisplayName("create：非超管建顶级部门 → 403")
    void createRootByNonSuperRejected() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::isSuperAdmin).thenReturn(false);

            R result = controller.insert(request(null, 0L, "AuditRoot2"));

            assertEquals(403, result.getCode());
            verify(service, never()).createDept(any());
        }
    }

    @Test
    @DisplayName("create：超管建顶级部门 → 200")
    void createRootBySuperAdminSucceeds() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::isSuperAdmin).thenReturn(true);

            R result = controller.insert(request(null, 0L, "AuditRoot2"));

            assertEquals(200, result.getCode());
            verify(service).createDept(any());
        }
    }

    @Test
    @DisplayName("delete：范围外部门 → 403 且不删除")
    void deleteOutOfScopeRejected() {
        when(deptScopeQuery.findVisibleDept(FOREIGN_DEPT)).thenReturn(null);

        R result = controller.delete(FOREIGN_DEPT);

        assertEquals(403, result.getCode());
        verify(service, never()).removeById(any(Long.class));
    }

    @Test
    @DisplayName("delete：范围内部门（无引用）→ 200 且删除")
    void deleteInScopeSucceeds() {
        when(deptScopeQuery.findVisibleDept(OWN_DEPT)).thenReturn(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA"));
        when(service.hasChildByDeptId(OWN_DEPT)).thenReturn(false);
        when(service.checkDeptExistUser(OWN_DEPT)).thenReturn(false);
        when(service.checkDeptExistRole(OWN_DEPT)).thenReturn(false);

        R result = controller.delete(OWN_DEPT);

        assertEquals(200, result.getCode());
        verify(service).removeById(OWN_DEPT);
    }

    @Test
    @DisplayName("userDeptTree：越权枚举他人部门 → 403，不泄露 deptId")
    void userDeptTreeForeignUserRejected() {
        when(userScopeQuery.findVisibleUser(7002L)).thenReturn(null);
        when(service.list(anyMap())).thenReturn(List.of());

        R result = controller.userDeptTree(7002L);

        assertEquals(403, result.getCode());
        assertEquals(null, result.getData());
    }

    @Test
    @DisplayName("userDeptTree：范围内用户 → 200，checkedKeys 为本部门")
    void userDeptTreeOwnUserSucceeds() {
        SysUser user = new SysUser();
        user.setId(7001L);
        user.setDeptId(OWN_DEPT);
        when(userScopeQuery.findVisibleUser(7001L)).thenReturn(user);
        when(sysUserService.getById(7001L)).thenReturn(user);
        when(service.list(anyMap())).thenReturn(List.of(dept(OWN_DEPT, PARENT_DEPT, "AuditDeptA")));

        R result = controller.userDeptTree(7001L);

        assertEquals(200, result.getCode());
        assertEquals("701", ((java.util.Map<?, ?>) result.getData()).get("checkedKeys"));
    }
}
