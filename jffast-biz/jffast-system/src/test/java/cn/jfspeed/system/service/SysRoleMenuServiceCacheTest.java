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

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.domain.dto.SysRoleMenuResponse;
import cn.jfspeed.system.entity.SysRoleMenu;
import cn.jfspeed.system.mapper.SysRoleMenuMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 角色按钮权限缓存用例：命中不走库、未命中去重后回填 TTL、变更时清理新旧两类键。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysRoleMenuServiceCacheTest {

    private static final String CACHE_KEY = "jffast:role:perms:5";

    @Mock
    private SysRoleMenuMapper baseMapper;

    @InjectMocks
    private SysRoleMenuService service;

    private MockedStatic<RedisUtil> redis;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        redis = mockStatic(RedisUtil.class);
    }

    @AfterEach
    void tearDown() {
        redis.close();
    }

    private SysRoleMenuResponse response(String perms) {
        SysRoleMenuResponse response = new SysRoleMenuResponse();
        response.setPerms(perms);
        return response;
    }

    @Test
    @DisplayName("缓存命中时应直接返回且不查库")
    void shouldReturnCachedPermsWithoutQuery() {
        List<String> cached = List.of("system:user:list");
        redis.when(() -> RedisUtil.get(CACHE_KEY)).thenReturn((Object) cached);

        List<String> perms = service.listAllBtnPerm(5L);

        assertSame(cached, perms);
        redis.verify(() -> RedisUtil.get(CACHE_KEY));
        org.mockito.Mockito.verifyNoInteractions(baseMapper);
    }

    @Test
    @DisplayName("缓存未命中应查库、过滤空值去重并按 TTL 回填")
    void shouldQueryAndCacheOnMiss() {
        when(baseMapper.listAllBtnPerm(5L)).thenReturn(List.of(
                response("system:user:list"),
                response("system:user:add"),
                response("system:user:list"),
                response("  "),
                response(null)));

        List<String> perms = service.listAllBtnPerm(5L);

        assertEquals(List.of("system:user:list", "system:user:add"), perms);
        redis.verify(() -> RedisUtil.set(CACHE_KEY, perms, 3600L));
    }

    @Test
    @DisplayName("清理单角色缓存应同时删除 TTL 键与两份历史哈希字段")
    void shouldClearRoleCacheIncludingLegacyHashes() {
        service.clearRoleCache(5L);

        redis.verify(() -> RedisUtil.del(CACHE_KEY));
        redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ROLE_PERM_LIST, "5"));
        redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ALL_MENU_ID_LIST, "5"));
    }

    @Test
    @DisplayName("清理全部角色缓存应删除前缀键与两份历史哈希")
    void shouldClearAllRoleCache() {
        redis.when(() -> RedisUtil.allKey("jffast:role:perms:*"))
                .thenReturn(new String[]{"jffast:role:perms:1", "jffast:role:perms:2"});

        service.clearAllRoleCache();

        redis.verify(() -> RedisUtil.del("jffast:role:perms:1", "jffast:role:perms:2"));
        redis.verify(() -> RedisUtil.del(BaseConstant.CACHE_ROLE_PERM_LIST));
        redis.verify(() -> RedisUtil.del(BaseConstant.CACHE_ALL_MENU_ID_LIST));
    }

    @Test
    @DisplayName("roleId 为空时不应触碰 Redis")
    void shouldSkipCacheClearWhenRoleIdNull() {
        service.clearRoleCache(null);

        redis.verifyNoInteractions();
    }

    @Test
    @DisplayName("save 成功应按角色清理缓存")
    void shouldClearCacheOnSave() {
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(5L);
        when(baseMapper.insert(roleMenu)).thenReturn(1);

        assertTrue(service.save(roleMenu));

        redis.verify(() -> RedisUtil.del(CACHE_KEY));
        redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ROLE_PERM_LIST, "5"));
        redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ALL_MENU_ID_LIST, "5"));
    }

    @Test
    @DisplayName("remove(wrapper) 成功应清理受影响角色的缓存")
    void shouldClearCacheOnRemoveByWrapper() {
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(5L);
        when(baseMapper.selectList(any())).thenReturn(List.of(roleMenu));
        when(baseMapper.delete(any())).thenReturn(1);

        assertTrue(service.remove(new LambdaQueryWrapper<>()));

        redis.verify(() -> RedisUtil.del(CACHE_KEY));
    }

    @Test
    @DisplayName("removeById 成功应按角色清理缓存")
    void shouldClearCacheOnRemoveById() {
        SysRoleMenu roleMenu = new SysRoleMenu();
        roleMenu.setRoleId(5L);
        when(baseMapper.selectById(5L)).thenReturn(roleMenu);
        when(baseMapper.deleteById(any(java.io.Serializable.class))).thenReturn(1);

        assertTrue(service.removeById(5L));

        redis.verify(() -> RedisUtil.del(CACHE_KEY));
    }
}
