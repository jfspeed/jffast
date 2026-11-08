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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import cn.jfspeed.system.entity.SysMenu;

/**
 * 租户管理菜单子的**覆盖式**检查：从 `sys_menu` 的 `parent_id` 递推整棵子树（不限层级），
 * 与 {@link SysRoleService#SUPER_ADMIN_ONLY_MENU_IDS} 比对，树里有而常量里没有的直接报红。
 * <p>
 * 与 {@code SysRoleMenuGrantGuardTest} 的分工：那个钉「常量里这些 id 的行为」，本类钉
 * 「常量是否覆盖真实子树」——后者才能抓到「以后新增了租户管理菜单、却没补进常量」。
 * <p>
 * 数据源是可注入的：{@link #collectSubtreeIds(List, long)} 只吃 {@code sys_menu} 记录集合，
 * 测试不连库。种子脚本存在时额外跑一遍真实种子（相对路径向上查找 {@code sql/pro_menu.sql}，
 * 找不到则跳过——Base 仓自身没有该文件，收编该种子的仓才实际执行）。
 *
 * @author jffast
 */
class SysRoleMenuGrantGuardCoverageTest {

    /** 租户管理根菜单：页面菜单（`/system/systemTenant/index`） */
    private static final long TENANT_ROOT_ID = 5014L;

    /** 种子脚本按此顺序查找：当前目录逐级向上，最多 5 层 */
    private static final String SEED_RELATIVE_PATH = "sql/pro_menu.sql";
    private static final int SEED_MAX_UP_LEVELS = 5;

    /** `(id, parent_id, 'name'` —— 只匹配 sys_menu 行；sys_role_menu 的 `(1, 5014)` 没有引号，不会误命中 */
    private static final Pattern MENU_ROW = Pattern.compile("\\((\\d+),\\s*(\\d+),\\s*'([^']*)'");

    // ------------------------------------------------------------------
    // 覆盖式检查的核心：取子树 -> 比常量（数据源可注入，不连库）
    // ------------------------------------------------------------------

    /**
     * 按 `parent_id` 递推收集子树 id（含根、不限层级、去环）。
     */
    static Set<Long> collectSubtreeIds(List<SysMenu> menus, long rootId) {
        Map<Long, List<SysMenu>> byParent = new HashMap<>();
        for (SysMenu menu : menus) {
            if (menu == null || menu.getId() == null || menu.getParentId() == null) {
                continue;
            }
            byParent.computeIfAbsent(menu.getParentId(), k -> new ArrayList<>()).add(menu);
        }
        Set<Long> subtree = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        subtree.add(rootId);
        while (!queue.isEmpty()) {
            List<SysMenu> children = byParent.get(queue.poll());
            if (children == null) {
                continue;
            }
            for (SysMenu child : children) {
                if (subtree.add(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return subtree;
    }

    /**
     * 断言子树里的每个菜单都登记在常量中；缺失时逐条报「新增了菜单 X（id=…），请补进常量」。
     */
    static void assertSubtreeCovered(List<SysMenu> menus, long rootId) {
        Map<Long, String> names = menus.stream()
                .filter(menu -> menu != null && menu.getId() != null)
                .collect(Collectors.toMap(SysMenu::getId, menu -> menu.getName() == null ? "" : menu.getName()));
        assertTrue(names.containsKey(rootId),
                () -> "菜单树里找不到租户管理根菜单（id=" + rootId + "），请确认根菜单是否被改名或删除");
        Set<Long> subtree = collectSubtreeIds(menus, rootId);
        List<String> missing = subtree.stream()
                .filter(id -> !SysRoleService.SUPER_ADMIN_ONLY_MENU_IDS.contains(id))
                .sorted()
                .map(id -> "新增了菜单「" + names.get(id) + "」（id=" + id
                        + "），请补进常量 SysRoleService.SUPER_ADMIN_ONLY_MENU_IDS")
                .collect(Collectors.toList());
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing));
    }

    // ------------------------------------------------------------------
    // 机制用例：递归深度 + 新增子菜单必红（夹具，不连库）
    // ------------------------------------------------------------------

    private static SysMenu menu(long id, long parentId, String name) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setName(name);
        return menu;
    }

    /** 以常量本身拼出「完全覆盖」的夹具树，再额外挂一条未登记菜单 */
    private static List<SysMenu> fixtureWithExtra(long extraId, long extraParentId, String extraName) {
        List<SysMenu> menus = new ArrayList<>();
        menus.add(menu(TENANT_ROOT_ID, 5000L, "租户管理"));
        SysRoleService.SUPER_ADMIN_ONLY_MENU_IDS.stream()
                .filter(id -> id != TENANT_ROOT_ID)
                .forEach(id -> menus.add(menu(id, TENANT_ROOT_ID, "菜单" + id)));
        menus.add(menu(extraId, extraParentId, extraName));
        return menus;
    }

    private static void assertRedWithId(Executable executable, long expectedId) {
        AssertionError error = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, executable);
        assertTrue(error.getMessage().contains("id=" + expectedId),
                () -> "报错未点名新增菜单 id=" + expectedId + "，实际：" + error.getMessage());
        assertTrue(error.getMessage().contains("请补进常量"),
                () -> "报错未提示补进常量，实际：" + error.getMessage());
    }

    @Test
    @DisplayName("子树按 parent_id 递推：不限层级（孙级也会收进来）")
    void collectsSubtreeRecursively() {
        List<SysMenu> menus = List.of(
                menu(TENANT_ROOT_ID, 5000L, "租户管理"),
                menu(5174L, TENANT_ROOT_ID, "租户查询"),
                menu(6001L, 5174L, "孙级菜单"));

        assertEquals(new LinkedHashSet<>(List.of(TENANT_ROOT_ID, 5174L, 6001L)),
                collectSubtreeIds(menus, TENANT_ROOT_ID));
    }

    @Test
    @DisplayName("根下新增子菜单未登记常量 → 覆盖检查报红并点名 id")
    void reportsNewChildUnderTenantRoot() {
        assertRedWithId(() -> assertSubtreeCovered(
                        fixtureWithExtra(5187L, TENANT_ROOT_ID, "租户导出台账"), TENANT_ROOT_ID),
                5187L);
    }

    @Test
    @DisplayName("深层新增孙级菜单未登记常量 → 同样报红（递归生效）")
    void reportsNewGrandchild() {
        assertRedWithId(() -> assertSubtreeCovered(
                        fixtureWithExtra(6002L, 5175L, "租户新增-批量"), TENANT_ROOT_ID),
                6002L);
    }

    @Test
    @DisplayName("常量覆盖完整子树时不报红")
    void passesWhenConstantCoversSubtree() {
        List<SysMenu> menus = new ArrayList<>();
        menus.add(menu(TENANT_ROOT_ID, 5000L, "租户管理"));
        SysRoleService.SUPER_ADMIN_ONLY_MENU_IDS.stream()
                .filter(id -> id != TENANT_ROOT_ID)
                .forEach(id -> menus.add(menu(id, TENANT_ROOT_ID, "菜单" + id)));

        assertSubtreeCovered(menus, TENANT_ROOT_ID);
    }

    // ------------------------------------------------------------------
    // 契约用例：真实种子里的子树必须与常量完全一致
    // ------------------------------------------------------------------

    private static Path locateSeed() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int level = 0; level <= SEED_MAX_UP_LEVELS && dir != null; level++, dir = dir.getParent()) {
            Path candidate = dir.resolve(SEED_RELATIVE_PATH);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<SysMenu> parseSeedMenus(Path seed) throws IOException {
        String text = Files.readString(seed, StandardCharsets.UTF_8);
        Matcher matcher = MENU_ROW.matcher(text);
        List<SysMenu> menus = new ArrayList<>();
        while (matcher.find()) {
            menus.add(menu(Long.parseLong(matcher.group(1)),
                    Long.parseLong(matcher.group(2)),
                    matcher.group(3)));
        }
        return menus;
    }

    @Test
    @DisplayName("契约：菜单种子里的租户管理子树必须全部登记在常量中")
    void seedSubtreeIsFullyRegistered() throws IOException {
        Path seed = locateSeed();
        Assumptions.assumeTrue(seed != null,
                "当前运行目录未提供 " + SEED_RELATIVE_PATH + "（Base 仓自身无该种子），跳过契约检查");

        List<SysMenu> menus = parseSeedMenus(seed);
        assertSubtreeCovered(menus, TENANT_ROOT_ID);
    }
}
