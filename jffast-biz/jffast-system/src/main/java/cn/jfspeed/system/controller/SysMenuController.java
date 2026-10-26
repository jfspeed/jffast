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
package cn.jfspeed.system.controller;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.Valid;
import cn.jfspeed.system.domain.dto.SysMenuSaveRequest;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.TreeUtil;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.domain.dto.RouterVo;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/menu")
@Tag(name = "菜单管理")
public class SysMenuController extends BaseController<SysMenuService,SysMenu> {

    // 登录态即可：返回当前用户自己的菜单树（前端装配路由用），故不设权限码
    @GetMapping("/routes")
    @Operation(summary = "获取当前用户菜单路由")
    public R<List<RouterVo>> routes() {
        List<Long> roleIdList = LoginUtil.getRoleIdList();
        boolean isSuperAdmin = roleIdList != null && roleIdList.stream()
                .anyMatch(id -> id.equals(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG));
        List<SysMenu> menuList;
        if (isSuperAdmin) {
            menuList = service.selectMenuList(new HashMap<>());
        } else if (roleIdList == null || roleIdList.isEmpty()) {
            // 无角色 = 无菜单（fail-closed），绝不因为 roleId 缺失而回落到全量菜单
            menuList = List.of();
        } else {
            // 多角色取并集，不能只看第一个角色
            menuList = service.selectMenuListByRoleIds(roleIdList);
        }
        menuList = menuList.stream()
                .filter(m -> !"F".equals(m.getType()))
                .collect(java.util.stream.Collectors.toList());
        List<SysMenu> tree = TreeUtil.buildTreeIsRoot(menuList, BaseConstant.PARENT_ID);
        List<RouterVo> routers = service.buildMenus(tree);
        return R.success(routers);
    }

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:view")
    @Operation(summary = "菜单树列表")
    public R list(@RequestParam Map<String,Object> map) {
        List<SysMenu> list = service.selectMenuList(map);
        // 复制列表避免修改原始数据影响树构建
        List<SysMenu> treeList = new java.util.ArrayList<>(list);
        return R.success(TreeUtil.buildTreeIsRoot(treeList,BaseConstant.PARENT_ID));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:view")
    @Operation(summary = "根据id获取信息")
    public R getById(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @GetMapping(value = "/roleMenuTree/{roleId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:view")
    @Operation(summary = "加载对应角色菜单列表树")
    public R roleMenuTree(@PathVariable("roleId") Long roleId) {
        Map<String,Object> map = new HashMap<>();
        List<SysMenu> menuList = service.selectMenuList(map);
        Map<String,Object> result = new HashMap<>();
        result.put("menuList", TreeUtil.buildTreeIsRoot(menuList,BaseConstant.PARENT_ID));
        result.put("checkedKeys", service.selectIdListByRoleId(roleId));
        return R.success(result);
    }

    @SLog(title = "新增菜单")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增菜单")
    public R insert(@RequestBody @Valid SysMenuSaveRequest req) {
        SysMenu menu = BeanUtil.copyProperties(req, SysMenu.class);
        if (service.checkMenuName(menu)) {
            return R.error("名称已存在");
        }
        if (StrUtil.isNotBlank(menu.getPath()) && service.checkMenuPathUnique(menu)) {
            return R.error("路由地址已存在");
        }
        menu.setId(IdUtils.snowflakeShort());
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        service.save(menu);
        return R.success();
    }

    @SLog(title = "修改菜单")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:update")
    @DistributedLock(key = "'menu:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改菜单")
    public R update(@RequestBody @Valid SysMenuSaveRequest req) {
        SysMenu menu = BeanUtil.copyProperties(req, SysMenu.class);
        if (service.checkMenuName(menu)) {
            return R.error("名称已存在");
        }
        if (StrUtil.isNotBlank(menu.getPath()) && service.checkMenuPathUnique(menu)) {
            return R.error("路由地址已存在");
        }
        if (menu.getId()!=null && menu.getParentId()!=null && menu.getParentId().equals(menu.getId())){
            return R.error("修改失败，上级不能是自己");
        }
        service.updateById(menu);
        return R.success();
    }

    @SLog(title = "删除菜单")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:menu:delete")
    @Operation(summary = "删除菜单")
    public R delete(@PathVariable("id") Long id) {
        if (service.hasChildByMenuId(id)) {
            return R.error("存在子菜单,不允许删除");
        }
        if (service.checkMenuExistRole(id)) {
            return R.error("该菜单已分配了角色，不允许删除");
        }
        service.removeById(id);
        return R.success();
    }
}
