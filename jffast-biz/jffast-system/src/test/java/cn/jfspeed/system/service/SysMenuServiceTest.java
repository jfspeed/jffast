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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.domain.dto.RouterVo;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.mapper.SysMenuMapper;
import cn.jfspeed.system.mapper.SysRoleMenuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SysMenuService 单元测试。
 * <p>
 * 覆盖路由树构建（buildMenus）、路径计算（getRouterPath）、唯一性校验
 * （checkMenuNameUnique/checkMenuPathUnique）、子菜单存在性（hasChildByMenuId）。
 * buildMenus/getRouterPath 为纯逻辑，无需 mock；唯一性校验 mock baseMapper。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysMenuServiceTest {

    @Mock
    private SysMenuMapper baseMapper;

    @Mock
    private SysRoleMenuService sysRoleMenuService;

    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;

    @InjectMocks
    private SysMenuService service;

    @BeforeEach
    void injectBaseMapper() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "sysRoleMenuService", sysRoleMenuService);
    }

    private SysMenu buildMenu(Long id, Long parentId, String name, String path, String url,
                              String type, Boolean visible, Integer sort) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parentId);
        m.setName(name);
        m.setPath(path);
        m.setUrl(url);
        m.setType(type);
        m.setVisible(visible);
        m.setSort(sort);
        return m;
    }

    @Nested
    @DisplayName("getRouterPath：路由路径计算")
    class GetRouterPath {

        @Test
        @DisplayName("M 类型 url 非 ParentView 应加前缀斜杠")
        void shouldPrefixSlashForMenuType() {
            SysMenu m = buildMenu(1L, 0L, "系统", "system", "Layout", "M", false, 1);
            assertEquals("/system", service.getRouterPath(m));
        }

        @Test
        @DisplayName("M 类型 url 为 ParentView 应保持原路径")
        void shouldKeepPathWhenParentView() {
            SysMenu m = buildMenu(1L, 0L, "系统", "system", BaseConstant.PARENT_VIEW, "M", false, 1);
            assertEquals("system", service.getRouterPath(m));
        }

        @Test
        @DisplayName("C 类型 parentId=0 应返回根路径")
        void shouldReturnRootPathForTopLevelComponent() {
            SysMenu m = buildMenu(2L, 0L, "用户", "user", "system/user/index", "C", false, 1);
            assertEquals("/", service.getRouterPath(m));
        }

        @Test
        @DisplayName("C 类型 parentId!=0 应返回原路径")
        void shouldReturnOriginalPathForNestedComponent() {
            SysMenu m = buildMenu(2L, 1L, "用户", "user", "system/user/index", "C", false, 1);
            assertEquals("user", service.getRouterPath(m));
        }

        @Test
        @DisplayName("F 按钮类型应返回原路径")
        void shouldReturnOriginalPathForButton() {
            SysMenu m = buildMenu(3L, 2L, "新增", "user:add", null, "F", false, 1);
            assertEquals("user:add", service.getRouterPath(m));
        }
    }

    @Nested
    @DisplayName("buildMenus：路由树构建")
    class BuildMenus {

        @Test
        @DisplayName("M 类型有 children 应设置 alwaysShow 和 redirect 并递归")
        void shouldBuildMenuWithChildren() {
            SysMenu child = buildMenu(2L, 1L, "用户", "user", "system/user/index", "C", false, 2);
            SysMenu parent = buildMenu(1L, 0L, "系统", "system", "Layout", "M", false, 1);
            parent.setChildren(Collections.singletonList(child));

            List<RouterVo> routers = service.buildMenus(Collections.singletonList(parent));

            assertEquals(1, routers.size());
            RouterVo router = routers.get(0);
            assertTrue(router.getAlwaysShow());
            assertEquals("noRedirect", router.getRedirect());
            assertNotNull(router.getChildren());
            assertEquals(1, router.getChildren().size());
            assertEquals("user", router.getChildren().get(0).getPath());
        }

        @Test
        @DisplayName("C 类型 parentId=0 应包装为单 children")
        void shouldWrapTopLevelComponentAsChild() {
            SysMenu menu = buildMenu(2L, 0L, "用户", "user", "system/user/index", "C", false, 1);

            List<RouterVo> routers = service.buildMenus(Collections.singletonList(menu));

            assertEquals(1, routers.size());
            RouterVo router = routers.get(0);
            assertEquals("/", router.getPath());
            assertNotNull(router.getChildren());
            assertEquals(1, router.getChildren().size());
            assertEquals("user", router.getChildren().get(0).getPath());
            assertEquals("system/user/index", router.getChildren().get(0).getComponent());
        }

        @Test
        @DisplayName("普通菜单无 children 应直接构建 router 且 children 为 null")
        void shouldBuildPlainMenuWithoutChildren() {
            SysMenu menu = buildMenu(3L, 1L, "角色", "role", "system/role/index", "C", false, 1);

            List<RouterVo> routers = service.buildMenus(Collections.singletonList(menu));

            assertEquals(1, routers.size());
            RouterVo router = routers.get(0);
            assertEquals("role", router.getPath());
            assertNull(router.getChildren());
            assertNotNull(router.getMeta());
            assertEquals(1, router.getMeta().getOrderNo());
        }
    }

    @Nested
    @DisplayName("checkMenuNameUnique：菜单名唯一性")
    class CheckMenuNameUnique {

        @Test
        @DisplayName("无重名应返回 false")
        void shouldReturnFalseWhenNoConflict() {
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            SysMenu menu = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            assertFalse(service.checkMenuNameUnique(menu));
        }

        @Test
        @DisplayName("重名且 id 不同应返回 true")
        void shouldReturnTrueWhenConflictWithDifferentId() {
            SysMenu info = buildMenu(2L, 0L, "用户", "user", null, "C", false, 1);
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(info);
            SysMenu menu = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            assertTrue(service.checkMenuNameUnique(menu));
        }

        @Test
        @DisplayName("重名但 id 相同（自身）应返回 false")
        void shouldReturnFalseWhenSameId() {
            SysMenu info = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(info);
            SysMenu menu = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            assertFalse(service.checkMenuNameUnique(menu));
        }
    }

    @Nested
    @DisplayName("checkMenuPathUnique：路径唯一性")
    class CheckMenuPathUnique {

        @Test
        @DisplayName("无冲突应返回 false")
        void shouldReturnFalseWhenNoConflict() {
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);
            SysMenu menu = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            assertFalse(service.checkMenuPathUnique(menu));
        }

        @Test
        @DisplayName("路径冲突且 id 不同应返回 true")
        void shouldReturnTrueWhenPathConflict() {
            SysMenu info = buildMenu(2L, 0L, "角色", "user", null, "C", false, 1);
            when(baseMapper.selectOne(any(QueryWrapper.class))).thenReturn(info);
            SysMenu menu = buildMenu(1L, 0L, "用户", "user", null, "C", false, 1);
            assertTrue(service.checkMenuPathUnique(menu));
        }
    }

    @Nested
    @DisplayName("hasChildByMenuId：子菜单存在性")
    class HasChildByMenuId {

        @Test
        @DisplayName("有子菜单应返回 true")
        void shouldReturnTrueWhenHasChild() {
            when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(2L);
            assertTrue(service.hasChildByMenuId(1L));
        }

        @Test
        @DisplayName("无子菜单应返回 false")
        void shouldReturnFalseWhenNoChild() {
            when(baseMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
            assertFalse(service.hasChildByMenuId(1L));
        }
    }

    @Nested
    @DisplayName("selectMenuListByRoleIds：多角色菜单并集")
    class SelectMenuListByRoleIds {

        @Test
        @DisplayName("roleIds 为 null 应返回空列表且不查库")
        void shouldReturnEmptyWhenRoleIdsNull() {
            assertTrue(service.selectMenuListByRoleIds(null).isEmpty());
            verify(baseMapper, never()).selectMenuListByRoleIds(any());
        }

        @Test
        @DisplayName("roleIds 为空应返回空列表且不查库")
        void shouldReturnEmptyWhenRoleIdsEmpty() {
            assertTrue(service.selectMenuListByRoleIds(Collections.emptyList()).isEmpty());
            verify(baseMapper, never()).selectMenuListByRoleIds(any());
        }

        @Test
        @DisplayName("非空 roleIds 应原样传给 mapper")
        @SuppressWarnings("unchecked")
        void shouldPassRoleIdsToMapper() {
            List<Long> roleIds = List.of(2L, 3L);
            List<SysMenu> menus = List.of(buildMenu(1L, 0L, "系统", "system", "Layout", "M", false, 1));
            when(baseMapper.selectMenuListByRoleIds(any())).thenReturn(menus);

            assertEquals(menus, service.selectMenuListByRoleIds(roleIds));

            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(baseMapper).selectMenuListByRoleIds(captor.capture());
            assertEquals(roleIds, captor.getValue().get("roleIds"));
        }
    }

    @Nested
    @DisplayName("listAllBtnPerm：超管按钮权限缓存")
    class ListAllBtnPerm {

        private static final String CACHE_KEY = "jffast:menu:allBtnPerm";

        @Test
        @DisplayName("缓存命中应直接返回且不查库")
        void shouldReturnCachedWithoutQuery() {
            List<String> cached = List.of("system:user:add");
            try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
                redis.when(() -> RedisUtil.get(CACHE_KEY)).thenReturn((Object) cached);

                assertSame(cached, service.listAllBtnPerm());
                verify(baseMapper, never()).selectList(any());
            }
        }

        @Test
        @DisplayName("缓存未命中应只保留 F 类型权限、去重并按 TTL 回填")
        void shouldFilterButtonsDedupeAndCacheWithTtl() {
            SysMenu add = buildMenu(10L, 1L, "新增", "add", null, "F", false, 1);
            add.setPerms("system:user:add");
            SysMenu addAgain = buildMenu(11L, 1L, "新增（重复）", "addAgain", null, "F", false, 2);
            addAgain.setPerms("system:user:add");
            SysMenu list = buildMenu(12L, 1L, "查询", "list", null, "F", false, 3);
            list.setPerms("system:user:list");
            SysMenu page = buildMenu(13L, 0L, "用户", "user", "system/user/index", "C", false, 4);
            page.setPerms("system:user:view");
            SysMenu blank = buildMenu(14L, 1L, "空权限", "blank", null, "F", false, 5);
            blank.setPerms("  ");
            when(baseMapper.selectList(any())).thenReturn(List.of(add, addAgain, list, page, blank));

            List<String> perms;
            try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
                redis.when(() -> RedisUtil.get(CACHE_KEY)).thenReturn(null);

                perms = service.listAllBtnPerm();

                redis.verify(() -> RedisUtil.set(CACHE_KEY, perms, 3600L));
            }
            assertEquals(List.of("system:user:add", "system:user:list"), perms);
        }
    }

    @Nested
    @DisplayName("菜单变更：权限缓存失效")
    class ClearPermissionCachesOnMutation {

        private static final String CACHE_KEY = "jffast:menu:allBtnPerm";

        @Test
        @DisplayName("save 成功应清理角色权限缓存与超管按钮缓存")
        void shouldClearCachesOnSave() {
            when(baseMapper.insert(any(SysMenu.class))).thenReturn(1);
            try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
                assertTrue(service.save(buildMenu(20L, 0L, "新增菜单", "newMenu", "Layout", "M", false, 1)));

                verify(sysRoleMenuService).clearAllRoleCache();
                redis.verify(() -> RedisUtil.del(CACHE_KEY));
            }
        }

        @Test
        @DisplayName("updateById 成功应清理角色权限缓存与超管按钮缓存")
        void shouldClearCachesOnUpdate() {
            when(baseMapper.updateById(any(SysMenu.class))).thenReturn(1);
            try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
                assertTrue(service.updateById(buildMenu(20L, 0L, "改名", "newMenu", "Layout", "M", false, 1)));

                verify(sysRoleMenuService).clearAllRoleCache();
                redis.verify(() -> RedisUtil.del(CACHE_KEY));
            }
        }

        @Test
        @DisplayName("removeById 成功应清理角色权限缓存与超管按钮缓存")
        void shouldClearCachesOnRemove() {
            when(baseMapper.deleteById(any(java.io.Serializable.class))).thenReturn(1);
            try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
                assertTrue(service.removeById(20L));

                verify(sysRoleMenuService).clearAllRoleCache();
                redis.verify(() -> RedisUtil.del(CACHE_KEY));
            }
        }
    }
}
