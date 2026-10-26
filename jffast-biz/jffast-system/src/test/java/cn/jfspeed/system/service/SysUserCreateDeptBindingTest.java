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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.spi.LoginPolicyProvider;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.controller.SysUserController;
import cn.jfspeed.system.domain.dto.SysUserSaveRequest;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * 创建路径部门绑定派生（D1）：只给 deptId 时落 1 行绑定，更新路径不派生。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserCreateDeptBindingTest {

    private static final Long GENERATED_ID = 9001L;
    private static final Long DEPT_ID = 10L;

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ObjectProvider<LoginPolicyProvider> loginPolicyProvider;

    @InjectMocks
    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(baseMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(GENERATED_ID);
            }
            return 1;
        });
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(sysDeptService.getById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            SysDept dept = new SysDept();
            dept.setId(id);
            dept.setDeptName("部门" + id);
            return dept;
        });
    }

    private static SysUser user(Long id, Long deptId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUserName("probe_dept_binding");
        user.setDeptId(deptId);
        return user;
    }

    private static SysDept deptRef(Long id) {
        SysDept dept = new SysDept();
        dept.setId(id);
        return dept;
    }

    @Test
    @DisplayName("方法1：非超管创建只给 deptId 时写入 1 条部门绑定")
    void nonSuperAdminCreateWithDeptIdWritesUserDeptBinding() {
        service.saveUser(user(null, DEPT_ID));

        verify(sysUserDeptMapper).insertBatch(argThat(list -> list.size() == 1
                && list.get(0).getDeptId().equals(DEPT_ID)
                && list.get(0).getUserId().equals(GENERATED_ID)));
    }

    @Test
    @DisplayName("方法5：创建只给 deptId 时恰写 1 条绑定（D1-LOCK，userId 取回填主键）")
    void createWithDeptIdAndNoDeptListWritesExactlyOneBinding() {
        service.saveUser(user(null, DEPT_ID));

        verify(sysUserDeptMapper).insertBatch(argThat(list -> list.size() == 1
                && list.get(0).getDeptId().equals(DEPT_ID)
                && list.get(0).getUserId().equals(GENERATED_ID)));
    }

    @Test
    @DisplayName("方法3：显式 deptList 保持原样，不被 deptId 覆盖（须包含归属部门）")
    void superAdminCreateWithExplicitDeptListKeepsExplicitBindings() {
        SysUser user = user(null, DEPT_ID);
        user.setDeptList(List.of(deptRef(5L), deptRef(7L), deptRef(DEPT_ID)));

        service.saveUser(user);

        verify(sysUserDeptMapper).insertBatch(argThat(list -> {
            if (list.size() != 3) {
                return false;
            }
            Set<Long> deptIds = list.stream().map(SysUserDept::getDeptId).collect(Collectors.toSet());
            return deptIds.equals(new HashSet<>(Set.of(5L, 7L, DEPT_ID)))
                    && list.stream().allMatch(row -> GENERATED_ID.equals(row.getUserId()));
        }));
    }

    @Test
    @DisplayName("方法3b（INV-3）：显式 deptList 不含 deptId 时拒绝创建，且不写库")
    void createWithExplicitDeptListMissingOwnerDeptIsRejected() {
        SysUser user = user(null, DEPT_ID);
        user.setDeptList(List.of(deptRef(5L), deptRef(7L)));

        cn.jfspeed.common.core.exception.BusinessException ex = org.junit.jupiter.api.Assertions
                .assertThrows(cn.jfspeed.common.core.exception.BusinessException.class,
                        () -> service.saveUser(user));

        assertEquals(403, ex.getCode());
        assertEquals("归属部门须包含在部门绑定中", ex.getMessage());
        verify(baseMapper, never()).insert(any(SysUser.class));
        verify(sysUserDeptMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("方法4：更新路径不因 deptId 派生绑定")
    void updatePathDoesNotDeriveBindingFromDeptId() {
        SysUser existing = user(9L, DEPT_ID);
        when(baseMapper.selectById(9L)).thenReturn(existing);

        service.saveUser(user(9L, DEPT_ID));

        verify(sysUserDeptMapper, never()).insertBatch(any());
        verify(sysUserDeptMapper, never()).delete(any());
    }

    @Test
    @DisplayName("方法2：非超管创建携带 deptList 由控制器拒绝，服务不被调用")
    void nonSuperAdminCreateWithDeptListRejectedByController() throws Exception {
        SysUserService controllerService = mock(SysUserService.class);
        SysUserController controller = new SysUserController(loginPolicyProvider);
        ReflectionTestUtils.setField(controller, "service", controllerService);
        ReflectionTestUtils.setField(controller, "sysRoleService", mock(SysRoleService.class));
        ReflectionTestUtils.setField(controller, "sysDeptService", mock(SysDeptService.class));
        ReflectionTestUtils.setField(controller, "sysDeptScopeQuery", mock(SysDeptScopeQuery.class));

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            LoginUser loginUser = new LoginUser();
            loginUser.setId(1L);
            loginUser.setUserName("operator");
            login.when(LoginUtil::getLoginUser).thenReturn(loginUser);
            login.when(LoginUtil::getRoleCodeList).thenReturn(List.of("common"));
            login.when(LoginUtil::getRoleIdList).thenReturn(List.of(2L));

            SysUserSaveRequest req = new SysUserSaveRequest();
            req.setUserName("probe_create_denied");
            req.setPassword("encrypted");
            SysUserSaveRequest.IdRef ref = new SysUserSaveRequest.IdRef();
            ref.setId(DEPT_ID);
            req.setDeptList(List.of(ref));

            R result = controller.insert(req);

            assertEquals(403, result.getCode());
            assertEquals("无权分配角色或部门", result.getMessage());
            verify(controllerService, never()).saveUser(any(), anyBoolean(), anyBoolean());
        }
    }
}
