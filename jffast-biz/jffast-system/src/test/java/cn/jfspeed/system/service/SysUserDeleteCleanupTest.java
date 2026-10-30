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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.constant.SystemErrorCode;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserPost;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysSocialBindMapper;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserPostMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;

/**
 * P3-2：删号必须连社交账号绑定一起清。
 *
 * <p>{@code sys_social_bind} 的唯一键是 {@code uk_provider_openid(provider, open_id)}：
 * 用户被删而绑定行留着，就把该社交账号永久占住了——既不能再绑到新账号，也回不到原账号，
 * 只能手工删库。同时该表由可选脚本创建，未启用社交时必须跳过，不能让删号 500。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserDeleteCleanupTest {

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysUserRoleMapper sysUserRoleMapper;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private SysUserPostMapper sysUserPostMapper;
    @Mock
    private SysSocialBindMapper sysSocialBindMapper;

    @InjectMocks
    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
        TableInfoHelper.initTableInfo(assistant, SysUserPost.class);
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        when(baseMapper.deleteById(anyLong())).thenReturn(1);
    }

    @Test
    @DisplayName("启用社交功能且表已导入时：删号同时清 sys_social_bind")
    void deletesSocialBindsWhenSocialEnabled() {
        ReflectionTestUtils.setField(service, "socialEnabled", true);
        when(sysSocialBindMapper.countSocialBindTable()).thenReturn(1);

        service.deleteUser(9L);

        verify(sysSocialBindMapper).deleteByUserId(9L);
        verify(sysUserRoleMapper).delete(any());
        verify(sysUserDeptMapper).delete(any());
        verify(sysUserPostMapper).delete(any());
        verify(baseMapper).deleteById(9L);
    }

    @Test
    @DisplayName("启用社交功能但表未导入时：跳过清理，删号照常成功（不得 500）")
    void skipsSocialBindsWhenTableNotImported() {
        // sys_social_bind 由可选脚本 sql/social_bind.sql 创建，不在 system_sql.sql 里：
        // 开了 jffast.social.enabled 不等于导了那张脚本，表不存在时无条件 DELETE 会让删号 500。
        ReflectionTestUtils.setField(service, "socialEnabled", true);
        when(sysSocialBindMapper.countSocialBindTable()).thenReturn(0);

        service.deleteUser(9L);

        verify(sysSocialBindMapper, never()).deleteByUserId(anyLong());
        verify(baseMapper).deleteById(9L);
    }

    @Test
    @DisplayName("未启用社交功能时：不触碰 sys_social_bind（该表可能根本没有导入）")
    void skipsSocialBindsWhenSocialDisabled() {
        ReflectionTestUtils.setField(service, "socialEnabled", false);

        service.deleteUser(9L);

        verify(sysSocialBindMapper, never()).deleteByUserId(anyLong());
        verify(sysSocialBindMapper, never()).countSocialBindTable();
        verify(baseMapper).deleteById(9L);
    }

    @Test
    @DisplayName("用户不存在时仍按原口径报错")
    void reportsMissingUser() {
        ReflectionTestUtils.setField(service, "socialEnabled", true);
        when(baseMapper.deleteById(anyLong())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteUser(404L));

        assertEquals(SystemErrorCode.USER_NOT_FOUND.getCode(), ex.getCode());
    }
}
