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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * N-16 / N-25：{@code sys_user.role_id} 是冗余缓存，必须显式写入（含空串），
 * 不能依赖 MP 全局 {@code NOT_EMPTY} 更新策略。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserRoleIdCacheTest {

    private static final Long GENERATED_ID = 1234L;

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

    @InjectMocks
    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        cn.jfspeed.system.entity.SysDept dept = new cn.jfspeed.system.entity.SysDept();
        dept.setId(10L);
        when(sysDeptService.getById(any())).thenReturn(dept);
        when(baseMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            // 模拟 MP ASSIGN_ID：insert 成功后把生成的主键回填到实体
            SysUser inserted = invocation.getArgument(0);
            if (inserted.getId() == null) {
                inserted.setId(GENERATED_ID);
            }
            return 1;
        });
        when(baseMapper.update(isNull(), any())).thenReturn(1);
    }

    private SysUser newUser(Long id, String userName) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUserName(userName);
        user.setDeptId(10L);
        return user;
    }

    private cn.jfspeed.system.entity.SysRole role(Long id) {
        cn.jfspeed.system.entity.SysRole role = new cn.jfspeed.system.entity.SysRole();
        role.setId(id);
        role.setDataScope(3);
        return role;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaUpdateWrapper<SysUser> captureCacheWrite() {
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<SysUser> wrapper = (LambdaUpdateWrapper<SysUser>) captor.getValue();
        // WHERE 参数由 MP 惰性写入，渲染 SQL 段落前参数表里只有 SET 参数
        wrapper.getSqlSegment();
        return wrapper;
    }

    @Test
    @DisplayName("创建 roleList=[] → 显式写 role_id=''")
    void createWithEmptyRoleListWritesEmptyString() {
        SysUser user = newUser(null, "probe_cache_empty");
        user.setRoleList(Collections.emptyList());
        when(sysRoleService.listByIds(any())).thenReturn(Collections.emptyList());

        service.saveUser(user);

        LambdaUpdateWrapper<SysUser> wrapper = captureCacheWrite();
        assertTrue(wrapper.getSqlSet().contains("role_id"), "缓存列必须出现在 SET 中");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(""), "空角色集合必须显式落空串");
        assertTrue(wrapper.getParamNameValuePairs().containsValue(GENERATED_ID),
                "缓存写入必须绑定 insert 回填后的用户 ID（P3-18），实际参数=" + wrapper.getParamNameValuePairs());
        assertEquals(GENERATED_ID, user.getId());
        assertEquals("", user.getRoleId());
    }

    @Test
    @DisplayName("创建 roleList=null → 不显式写，DB 保持 NULL")
    void createWithNullRoleListKeepsNull() {
        SysUser user = newUser(null, "probe_cache_null");

        service.saveUser(user);

        assertNull(user.getRoleId());
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("更新 roleList=[] → 显式写 role_id=''")
    void updateWithEmptyRoleListWritesEmptyString() {
        SysUser existing = newUser(9L, "probe_cache_u");
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserDeptMapper.selectList(any())).thenReturn(Collections.emptyList());
        SysUser user = newUser(9L, "probe_cache_u");
        user.setRoleList(Collections.emptyList());

        service.saveUser(user);

        LambdaUpdateWrapper<SysUser> wrapper = captureCacheWrite();
        assertTrue(wrapper.getParamNameValuePairs().containsValue(""));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(9L),
                "更新缓存必须绑定目标用户 ID（P3-18）");
    }

    @Test
    @DisplayName("更新 roleList=null → 不触碰缓存列")
    void updateWithNullRoleListDoesNotTouchCache() {
        SysUser existing = newUser(9L, "probe_cache_u2");
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.updateById(any(SysUser.class))).thenReturn(1);
        SysUser user = newUser(9L, "probe_cache_u2");
        user.setRoleId("5");

        service.saveUser(user);

        assertEquals("5", user.getRoleId());
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("非空 roleList → 拼接值写入缓存")
    void nonEmptyRoleListJoinsIds() {
        SysUser user = newUser(null, "probe_cache_join");
        List<cn.jfspeed.system.entity.SysRole> roles = Arrays.asList(role(2L), role(7L));
        user.setRoleList(roles);
        when(sysRoleService.listByIds(any())).thenReturn(roles);

        service.saveUser(user);

        LambdaUpdateWrapper<SysUser> wrapper = captureCacheWrite();
        assertTrue(wrapper.getParamNameValuePairs().containsValue("2,7"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(GENERATED_ID),
                "缓存写入必须绑定 insert 回填后的用户 ID（P3-18），实际参数=" + wrapper.getParamNameValuePairs());
        assertEquals("2,7", user.getRoleId());
    }
}
