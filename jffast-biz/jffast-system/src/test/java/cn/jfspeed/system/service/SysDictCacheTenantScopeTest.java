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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.List;

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
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.entity.SysDict;
import cn.jfspeed.system.mapper.SysDictMapper;

/**
 * P3-7：字典项缓存键必须带租户维度。
 *
 * <p>字典行按租户隔离（多租户形态下拦截器追加 {@code tenant_id}），缓存键不含租户时，
 * 先被读到的那家字典会被所有租户复用——跨租户数据串用。这里钉住键的形状：
 * {@code jffast:dict:type:<tenantId>:<code>}，且读写/失效三处用同一个键。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDictCacheTenantScopeTest {

    private static final String DICT_CODE = "sys_status";

    @Mock
    private SysDictMapper baseMapper;

    @InjectMocks
    private SysDictService service;

    @BeforeAll
    static void initTableInfoCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDict.class);
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        SysDict type = new SysDict();
        type.setId(1L);
        type.setCode(DICT_CODE);
        type.setIsType(1);
        SysDict item = new SysDict();
        item.setId(2L);
        item.setParentId(1L);
        item.setCode("qy");
        item.setName("启用");
        item.setValue("1");
        item.setIsType(2);
        when(baseMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<SysDict> wrapper = invocation.getArgument(0);
            return wrapper.getSqlSegment().contains("is_type =") ? List.of(type) : List.of(item);
        });
    }

    private LoginUser loginUserOfTenant(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(100L);
        user.setTenantId(tenantId);
        return user;
    }

    @Test
    @DisplayName("读缓存命中/回填都用带租户的键")
    void readAndWriteUseTenantScopedKey() {
        String expectedKey = "jffast:dict:type:7:" + DICT_CODE;
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUserOfTenant(7L));
            redis.when(() -> RedisUtil.get(anyString())).thenReturn(null);

            List<SysDict> list = service.getListByDictCode(DICT_CODE);

            assertEquals(1, list.size());
            redis.verify(() -> RedisUtil.get(expectedKey));
            redis.verify(() -> RedisUtil.set(eq(expectedKey), any(), eq(3600L)));
        }
    }

    @Test
    @DisplayName("不同租户读不到彼此的缓存条目（键不同）")
    void differentTenantsUseDifferentKeys() {
        String[] keys = new String[2];
        Long[] tenants = {7L, 8L};
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.get(anyString())).thenReturn(null);
            for (int i = 0; i < tenants.length; i++) {
                Long tenantId = tenants[i];
                login.when(LoginUtil::getLoginUser).thenReturn(loginUserOfTenant(tenantId));
                String[] captured = new String[1];
                redis.when(() -> RedisUtil.set(anyString(), any(), eq(3600L)))
                        .thenAnswer(invocation -> {
                            captured[0] = invocation.getArgument(0);
                            return true;
                        });
                service.getListByDictCode(DICT_CODE);
                keys[i] = captured[0];
            }
        }
        assertNotEquals(keys[0], keys[1], "不同租户的字典缓存键不能相同");
        assertEquals("jffast:dict:type:7:" + DICT_CODE, keys[0]);
        assertEquals("jffast:dict:type:8:" + DICT_CODE, keys[1]);
    }

    @Test
    @DisplayName("无登录态落固定维度键，不与任何租户键冲突")
    void anonymousUsesNoTenantScope() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(null);
            redis.when(() -> RedisUtil.get(anyString())).thenReturn(null);

            service.getListByDictCode(DICT_CODE);

            redis.verify(() -> RedisUtil.get("jffast:dict:type:0:" + DICT_CODE));
        }
    }

    @Test
    @DisplayName("失效缓存与读取用同一个带租户的键")
    void cacheEvictionUsesSameTenantScopedKey() {
        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class);
             MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUserOfTenant(7L));

            service.clearDictCache(DICT_CODE);

            redis.verify(() -> RedisUtil.del("jffast:dict:type:7:" + DICT_CODE));
        }
    }
}
