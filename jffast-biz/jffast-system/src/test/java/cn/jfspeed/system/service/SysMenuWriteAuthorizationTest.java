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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.entity.SysMenu;

/**
 * 菜单写侧授权：防止「持 system:menu:update 的低权管理员改写自己角色已绑定菜单的 perms，
 * 重新登录后获得任意权限码」这条提权链。
 *
 * <p>复现条件（线上实测）：操作者角色绑定了菜单 1303，其 perms 为 system:menu:update；
 * 操作者本身不含 system:config:update。把 1303 的 perms 改成 system:config:update 后重登，
 * 即可调用 /sys/config/update。因此这里钉住：非超管改 perms 时，新权限码必须已在操作者
 * 自己的权限集合内。</p>
 *
 * @author jffast
 */
class SysMenuWriteAuthorizationTest {

    private static SysMenu menu(Long id, Long parentId, String perms) {
        SysMenu menu = new SysMenu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setPerms(perms);
        return menu;
    }

    private static SysMenuService.MenuOperatorContext operator(Long id, boolean superAdmin,
                                                               Set<Long> menuIds, Set<String> perms) {
        return new SysMenuService.MenuOperatorContext(id, menuIds, perms, superAdmin);
    }

    @Test
    @DisplayName("非超管改自己角色菜单的 perms：新权限码不在自己权限集合内 → 拒绝")
    void nonSuperAdminCannotEscalateOwnMenuPerms() {
        SysMenu existing = menu(1303L, 1003L, "system:menu:update");
        SysMenu incoming = menu(1303L, 1003L, "system:config:update");
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertThrows(BusinessException.class,
                () -> SysMenuService.validateMenuWrite(incoming, existing, op));
    }

    @Test
    @DisplayName("非超管改别人角色菜单的 perms：菜单本身不在自己菜单集合内 → 拒绝")
    void nonSuperAdminCannotUpdateForeignMenu() {
        SysMenu existing = menu(1304L, 1003L, "system:user:update");
        SysMenu incoming = menu(1304L, 1003L, "system:config:update");
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertThrows(BusinessException.class,
                () -> SysMenuService.validateMenuWrite(incoming, existing, op));
    }

    @Test
    @DisplayName("超管正常改菜单（含 perms）→ 放行")
    void superAdminCanUpdateMenu() {
        SysMenu existing = menu(1303L, 1003L, "system:menu:update");
        SysMenu incoming = menu(1303L, 1003L, "system:config:update");
        SysMenuService.MenuOperatorContext op =
                operator(1L, true, Set.of(), Set.of());

        assertDoesNotThrow(() -> SysMenuService.validateMenuWrite(incoming, existing, op));
    }

    @Test
    @DisplayName("新增菜单时指定 perms：挂在自有菜单下 → 放行（未绑定角色前权限码不生效）")
    void nonSuperAdminCanCreateMenuWithNewPerms() {
        SysMenu incoming = menu(2100L, 1003L, "system:audit:add");
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertDoesNotThrow(() -> SysMenuService.validateMenuWrite(incoming, null, op));
    }

    @Test
    @DisplayName("新增顶级菜单：非超管 → 拒绝")
    void nonSuperAdminCannotCreateRootMenu() {
        SysMenu incoming = menu(2101L, 0L, "system:audit:add");
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertThrows(BusinessException.class,
                () -> SysMenuService.validateMenuWrite(incoming, null, op));
    }

    @Test
    @DisplayName("非超管只改名称/排序等非敏感字段（perms 未变）→ 放行")
    void nonSuperAdminCanUpdateNonPermFieldsOfOwnMenu() {
        SysMenu existing = menu(1303L, 1003L, "system:menu:update");
        SysMenu incoming = menu(1303L, 1003L, "system:menu:update");
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertDoesNotThrow(() -> SysMenuService.validateMenuWrite(incoming, existing, op));
    }

    @Test
    @DisplayName("请求未携带 perms（null 表示不更新该字段）→ 放行")
    void nullPermsMeansUnchanged() {
        SysMenu existing = menu(1303L, 1003L, "system:menu:update");
        SysMenu incoming = menu(1303L, 1003L, null);
        SysMenuService.MenuOperatorContext op =
                operator(7001L, false, Set.of(1003L, 1303L), Set.of("system:menu:update"));

        assertDoesNotThrow(() -> SysMenuService.validateMenuWrite(incoming, existing, op));
    }
}
