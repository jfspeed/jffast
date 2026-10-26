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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import cn.hutool.extra.spring.SpringUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;

/**
 * 登录后菜单 ID 回写必须带 TTL（{@code jffast:getAllMenuIdList:} 哈希）。
 * <p>
 * 权威绑定在 sys_role_menu，Redis 里那个字段只是加速副本。曾经用不带 TTL 的
 * {@code hset(key, field, value)} 回写：只要有一条路径漏删（进程被 kill -9、
 * 直接改库、升级前的旧键），旧菜单 ID 就永久命中，菜单权限收紧在鉴权上永远不生效，
 * 且没有任何自愈时间。带 TTL 后漏删最坏只影响一个周期。
 * <p>
 * 断言按"必须走带 TTL 的重载"和"绝不走无 TTL 重载"成对写：只断前者时，
 * 实现改成两个重载都调一次也能过。
 *
 * @author jffast
 */
class LoginUtilMenuIdCacheTtlTest {

    private static final Long ROLE_ID = 9L;
    private static final String FIELD = String.valueOf(ROLE_ID);
    private static final List<Long> MENU_IDS = List.of(11L, 12L);

    private final SysRoleMenuService roleMenuService = mock(SysRoleMenuService.class);

    private MockedStatic<SpringUtil> spring;
    private MockedStatic<RedisUtil> redis;

    @BeforeEach
    void setUp() {
        // LoginUtil 全程用反射取 bean：RedisUtil 类本身被静态桩接管，回传的实例只是占位
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtil.getBean(RedisUtil.class)).thenReturn(mock(RedisUtil.class));
        spring.when(() -> SpringUtil.getBean(SysRoleMenuService.class)).thenReturn(roleMenuService);

        redis = mockStatic(RedisUtil.class);
        // 默认 hget 返回 null（未命中），才会走查库 + 回写分支
    }

    @AfterEach
    void tearDown() {
        redis.close();
        spring.close();
    }

    @Test
    @DisplayName("未命中时查库回写，并带上 3600 秒 TTL")
    void writeBackCarriesTtl() {
        when(roleMenuService.listAllMenuIdList(ROLE_ID)).thenReturn(MENU_IDS);

        List<Long> menuIds = LoginUtil.getAllMenuIdList(List.of(ROLE_ID));

        assertEquals(MENU_IDS, menuIds, "查库结果必须原样返回");
        redis.verify(() -> RedisUtil.hset(BaseConstant.CACHE_ALL_MENU_ID_LIST, FIELD, MENU_IDS, 3600L));
    }

    @Test
    @DisplayName("不得再用无 TTL 的 hset 重载回写菜单 ID")
    void neverWritesWithoutTtl() {
        when(roleMenuService.listAllMenuIdList(ROLE_ID)).thenReturn(MENU_IDS);

        LoginUtil.getAllMenuIdList(List.of(ROLE_ID));

        redis.verify(() -> RedisUtil.hset(anyString(), anyString(), any()), never());
    }

    @Test
    @DisplayName("命中缓存时直接返回，不查库也不续期")
    void cacheHitSkipsDbAndRewrite() {
        redis.when(() -> RedisUtil.hget(BaseConstant.CACHE_ALL_MENU_ID_LIST, FIELD)).thenReturn(MENU_IDS);

        List<Long> menuIds = LoginUtil.getAllMenuIdList(List.of(ROLE_ID));

        assertEquals(MENU_IDS, menuIds);
        redis.verify(() -> RedisUtil.hset(anyString(), anyString(), any(), anyLong()), never());
        verify(roleMenuService, never()).listAllMenuIdList(ROLE_ID);
    }
}
