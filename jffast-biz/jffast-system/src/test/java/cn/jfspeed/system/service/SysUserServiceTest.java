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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * SysUserService 单元测试。
 * <p>
 * 覆盖 checkUserNameAndPhone（唯一性校验）、getUser（查询 + BusinessException 异常）、
 * setDataScope（数据权限范围 1/2/4 三种场景）。@BeforeAll 初始化 SysUser/SysUserRole/SysUserDept
 * TableInfo 以支持 LambdaQueryWrapper 列名解析。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserServiceTest {

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysDeptService sysDeptService;
    @Mock
    private SysRoleService sysRoleService;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserScopeQuery sysUserScopeQuery;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private cn.jfspeed.common.core.config.JfFastSystemProperties jfFastSystemProperties;

    @InjectMocks
    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        // 初始化 SysUser/SysUserRole/SysUserDept TableInfo，使 LambdaQueryWrapper 能解析列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
    }

    @BeforeEach
    void injectBaseMapper() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // N-20：非空 deptId 必须存在；默认放行测试使用的部门
        cn.jfspeed.system.entity.SysDept dept = new cn.jfspeed.system.entity.SysDept();
        dept.setId(10L);
        dept.setDeptName("技术部");
        when(sysDeptService.getById(any())).thenReturn(dept);
        // role_id 缓存走显式 LambdaUpdateWrapper 写入
        when(baseMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
    }

    private SysUser buildUser(Long id, String userName, Long deptId, String deptName) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUserName(userName);
        u.setDeptId(deptId);
        u.setDeptName(deptName);
        return u;
    }

    private SysRole buildRole(Long id, Integer dataScope) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setDataScope(dataScope);
        return r;
    }

    @Nested
    @DisplayName("checkUserNameAndPhone：用户名/手机号唯一性")
    class CheckUserNameAndPhone {

        @Test
        @DisplayName("无冲突应返回 false")
        void shouldReturnFalseWhenNoConflict() {
            when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            assertFalse(service.checkUserNameAndPhone("admin", "13800138000", null));
        }

        @Test
        @DisplayName("有冲突应返回 true")
        void shouldReturnTrueWhenConflict() {
            when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);
            assertTrue(service.checkUserNameAndPhone("admin", null, 1L));
        }
    }

    @Nested
    @DisplayName("getUser：查询用户")
    class GetUser {

        @Test
        @DisplayName("用户不存在应抛 BusinessException(1001)")
        void shouldThrowWhenUserNotFound() {
            when(baseMapper.getById(any())).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getUser(1L, null, null));
            assertEquals(1001, ex.getCode());
            assertEquals("用户不存在", ex.getMessage());
        }

        @Test
        @DisplayName("手机号不存在应抛 BusinessException 含手机号信息")
        void shouldThrowWhenPhoneNotFound() {
            when(baseMapper.getById(any())).thenReturn(null);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.getUser(null, null, "13800138000"));
            assertEquals(1001, ex.getCode());
            assertTrue(ex.getMessage().contains("13800138000"));
        }

        @Test
        @DisplayName("用户存在且有角色应设置 roleList")
        void shouldSetRoleListWhenUserHasRoles() {
            SysUser user = buildUser(1L, "admin", 10L, "技术部");
            when(baseMapper.getById(any())).thenReturn(user);
            when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.singletonList(new SysUserRole(1L, 2L)));
            when(sysRoleService.listByIds(any())).thenReturn(Collections.singletonList(buildRole(2L, 1)));
            when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());

            SysUser result = service.getUser(1L, "admin", null);

            assertNotNull(result);
            assertEquals("admin", result.getUserName());
            assertNotNull(result.getRoleList());
            assertEquals(1, result.getRoleList().size());
        }
    }

    @Nested
    @DisplayName("saveUser：维护遗留列 sys_user.role_id（与 sys_user_role 关系表同步）")
    class SaveUser {

        @Test
        @DisplayName("新增非超管用户应把 roleList 拼接写入 role_id")
        void shouldJoinRoleIdsWhenCreate() {
            SysUser user = buildUser(null, "probe_ds_u1", 10L, "技术部");
            user.setRoleList(java.util.Arrays.asList(buildRole(2L, 3), buildRole(7L, 4)));
            when(sysRoleService.listByIds(any())).thenReturn(user.getRoleList());

            service.saveUser(user);

            assertEquals("2,7", user.getRoleId());
        }

        @Test
        @DisplayName("roleList 为空列表应把 role_id 置空串（与关系表清空一致）")
        void shouldSetEmptyRoleIdWhenNoRoles() {
            SysUser user = buildUser(null, "probe_ds_u2", 10L, "技术部");
            user.setRoleList(Collections.emptyList());

            service.saveUser(user);

            assertEquals("", user.getRoleId());
        }

        @Test
        @DisplayName("roleList 指向已删除角色时应拒绝创建")
        void shouldRejectUnknownRoleWhenCreate() {
            SysUser user = buildUser(null, "probe_ds_u4", 10L, "技术部");
            user.setRoleList(Collections.singletonList(buildRole(99L, 3)));
            when(sysRoleService.listByIds(any())).thenReturn(Collections.emptyList());

            BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(user));

            assertTrue(ex.getMessage().contains("角色不存在"));
        }

        @Test
        @DisplayName("更新不存在的用户应拒绝，避免静默成功")
        void shouldRejectUpdateWhenUserMissing() {
            SysUser user = buildUser(404L, "ghost", 10L, "技术部");
            when(baseMapper.selectById(404L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, () -> service.saveUser(user));

            assertTrue(ex.getMessage().contains("用户不存在"));
        }

        @Test
        @DisplayName("roleList 为 null（请求未携带角色）不应改动 role_id")
        void shouldKeepRoleIdWhenRoleListAbsent() {
            SysUser user = buildUser(9L, "probe_ds_u3", 10L, "技术部");
            user.setRoleId("5");
            when(baseMapper.selectById(9L)).thenReturn(user);
            when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);

            service.saveUser(user);

            assertEquals("5", user.getRoleId());
        }

        @Test
        @DisplayName("超管用户即使携带 roleList 也不改 role_id（与关系表跳过一致）")
        void shouldNotTouchRoleIdForSuperAdmin() {
            SysUser user = buildUser(1L, BaseConstant.SUPER_ADMIN, 10L, "技术部");
            user.setRoleList(Collections.singletonList(buildRole(2L, 3)));
            when(baseMapper.selectById(1L)).thenReturn(user);
            when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);

            service.saveUser(user);

            assertNull(user.getRoleId());
        }

        @Test
        @DisplayName("创建仅给 deptId 时应派生 1 条部门绑定（D1-LOCK）")
        void createWithDeptIdWritesOneDeptBinding() {
            SysUser user = buildUser(null, "probe_ds_u5", 10L, "技术部");
            when(baseMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
                SysUser inserted = invocation.getArgument(0);
                if (inserted.getId() == null) {
                    inserted.setId(9001L);
                }
                return 1;
            });

            service.saveUser(user);

            verify(sysUserDeptMapper).insertBatch(argThat(list -> list.size() == 1
                    && list.get(0).getDeptId().equals(10L)
                    && list.get(0).getUserId().equals(9001L)));
        }
    }

    @Nested
    @DisplayName("setDataScope：数据权限范围设置")
    class SetDataScope {

        @Test
        @DisplayName("dataScope=1 全部数据应写显式空 map（全量）")
        void shouldUseEmptyMapWhenAllDataScope() {
            SysUser user = buildUser(1L, "admin", 10L, "技术部");
            user.setRoleList(Collections.singletonList(buildRole(2L, 1)));

            service.setDataScope(user);

            assertNotNull(user.getDataScope());
            assertTrue(user.getDataScope().isEmpty());
        }

        @Test
        @DisplayName("dataScope=4 仅本人应设置 SELF")
        void shouldSetSelfWhenDataScopeIsSelf() {
            SysUser user = buildUser(1L, "admin", 10L, "技术部");
            user.setRoleList(Collections.singletonList(buildRole(2L, 4)));

            service.setDataScope(user);

            assertNotNull(user.getDataScope());
            assertEquals(1L, user.getDataScope().get(BaseConstant.DATA_SCOPE_SELF));
            assertEquals("10", user.getDataScope().get(BaseConstant.DATA_SCOPE_DEPT_ID));
        }

        @Test
        @DisplayName("dataScope=2 自定义部门应设置父部门，且不写本部门条件")
        void shouldSetParentDeptWhenDataScopeIsCustom() {
            SysUser user = buildUser(1L, "admin", 10L, "技术部");
            user.setRoleList(Collections.singletonList(buildRole(2L, 2)));
            Map<String, String> childMap = new HashMap<>();
            childMap.put("id", "5");
            childMap.put("name", "研发中心");
            when(sysDeptService.getChildIdAndName(10L)).thenReturn(childMap);

            service.setDataScope(user);

            assertNotNull(user.getDataScope());
            assertEquals("5", user.getDataScope().get(BaseConstant.DATA_SCOPE_DEPT_PARENT_ID));
            assertEquals("研发中心", user.getDataScope().get(BaseConstant.DATA_SCOPE_DEPT_PARENT_NAME));
            assertFalse(user.getDataScope().containsKey(BaseConstant.DATA_SCOPE_DEPT_ID));
        }

        @Test
        @DisplayName("无角色应落 none（fail-closed）")
        void shouldFallToNoneWhenNoRoles() {
            SysUser user = buildUser(1L, "admin", 10L, "技术部");
            user.setRoleList(null);

            service.setDataScope(user);

            assertEquals(Boolean.TRUE, user.getDataScope().get(BaseConstant.DATA_SCOPE_NONE));
        }
    }
}
