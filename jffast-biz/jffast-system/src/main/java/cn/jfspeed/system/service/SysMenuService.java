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

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.redis.util.RedisUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.jfspeed.system.domain.dto.SysMenuRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.entity.SysRoleMenu;
import cn.jfspeed.system.mapper.SysMenuMapper;
import cn.jfspeed.system.mapper.SysRoleMenuMapper;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.core.util.TreeUtil;
import cn.jfspeed.system.domain.dto.MetaVo;
import cn.jfspeed.system.domain.dto.RouterVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.io.Serializable;
import java.util.*;

/**
 * @author jffast
 */
@Service
public class SysMenuService extends BaseService<SysMenuMapper, SysMenu>{

    /** 超管全量按钮权限缓存（菜单定义变更时失效） */
    private static final String CACHE_ALL_BTN_PERM = BaseConstant.CACHE_PREFIX + "menu:allBtnPerm";
    private static final long ALL_BTN_PERM_TTL = 3600;

    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private SysRoleMenuMapper sysRoleMenuMapper;

    public List<SysMenu> selectPermsByRoleId(Map<String,Object> map){
        return baseMapper.selectPermsByRoleId(map);
    }

    public List<SysMenu> selectMenuList(Map<String,Object> map) {
        List<SysMenu> menuList = null;
        Object idObj = map.get("id");
        if (idObj == null || StrUtil.isEmpty(idObj.toString())){
            QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
            if (StrUtil.isNotBlank((String)map.get("name"))) {
                queryWrapper.like("name", map.get("name"));
            }
            if (StrUtil.isNotBlank((String)map.get("state"))) {
                queryWrapper.eq("state", map.get("state"));
            }
            queryWrapper.orderByAsc("parent_id,sort");
            menuList = baseMapper.selectList(queryWrapper);
        }else {
            menuList = baseMapper.selectMenuListByRoleId(map);
        }
        return menuList;
    }

    /** 按多个角色取菜单并集（同一菜单只返回一次） */
    public List<SysMenu> selectMenuListByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Object> map = new HashMap<>();
        map.put("roleIds", roleIds);
        return baseMapper.selectMenuListByRoleIds(map);
    }

    /** 超管按钮权限全集（带 TTL 缓存，避免每次鉴权都全表查询） */
    @SuppressWarnings("unchecked")
    public List<String> listAllBtnPerm() {
        Object cached = RedisUtil.get(CACHE_ALL_BTN_PERM);
        if (cached instanceof List) {
            return (List<String>) cached;
        }
        List<String> perms = selectMenuList(new HashMap<>()).stream()
                .filter(menu -> "F".equals(menu.getType()))
                .map(SysMenu::getPerms)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        RedisUtil.set(CACHE_ALL_BTN_PERM, perms, ALL_BTN_PERM_TTL);
        return perms;
    }

    private void clearPermissionCaches() {
        sysRoleMenuService.clearAllRoleCache();
        // 与角色缓存同批挂到事务提交后：菜单行在提交前对其它读事务仍是旧值，
        // 此刻删键会被并发鉴权请求用旧权限回填，并存活到 TTL 到期。
        SysRoleMenuService.deferAfterCommit(() -> RedisUtil.del(CACHE_ALL_BTN_PERM));
    }

    @Override
    public boolean save(SysMenu entity) {
        boolean result = super.save(entity);
        if (result) {
            clearPermissionCaches();
        }
        return result;
    }

    @Override
    public boolean updateById(SysMenu entity) {
        boolean result = super.updateById(entity);
        if (result) {
            clearPermissionCaches();
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            clearPermissionCaches();
        }
        return result;
    }

    public List<SysMenu> selectMenuListFullPath(Map<String,Object> map) {
        List<SysMenu> menuList = null;
        Object idObj = map.get("id");
        if (idObj == null || StrUtil.isEmpty(idObj.toString())){
            QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
            if (StrUtil.isNotBlank((String)map.get("name"))) {
                queryWrapper.like("name", map.get("name"));
            }
            if (StrUtil.isNotBlank((String)map.get("state"))) {
                queryWrapper.eq("state", map.get("state"));
            }
            queryWrapper.orderByAsc("parent_id,sort");
            menuList = baseMapper.selectList(queryWrapper);
        }else {
            menuList = baseMapper.selectMenuListByRoleId(map);
        }
        return menuList;
    }

    public List<SysMenu> selectMenuTreeByRoleId(Map<String,Object> map) {
        List<SysMenu> menus = baseMapper.selectMenuTreeByRoleId(map);
        return TreeUtil.buildTreeIsRoot(menus,BaseConstant.PARENT_ID);
    }

    public List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new LinkedList<>();
        for (SysMenu menu : menus) {
            RouterVo router = new RouterVo();
            router.setHidden(menu.getVisible());
            router.setName(StrUtil.upperFirst(menu.getPath()));
            router.setPath(getRouterPath(menu));
            router.setComponent(menu.getUrl());
            MetaVo meta = new MetaVo(menu.getName(), menu.getIcon(), menu.getActiveMenu());
            meta.setOrderNo(menu.getSort());
            meta.setHidden(menu.getVisible() != null && menu.getVisible());
            router.setMeta(meta);
            List<SysMenu> cMenus = menu.getChildren();
            if (cMenus != null && !cMenus.isEmpty() && "M".equals(menu.getType())) {
                router.setAlwaysShow(true);
                router.setRedirect("noRedirect");
                router.setChildren(buildMenus(cMenus));
            }else if ("C".equals(menu.getType()) && BaseConstant.PARENT_ID.equals(menu.getParentId())){
                List<RouterVo> childrenList = new ArrayList<>();
                RouterVo children = new RouterVo();
                children.setName(StrUtil.upperFirst(menu.getPath()));
                children.setPath(menu.getPath());
                children.setComponent(menu.getUrl());
                MetaVo childMeta = new MetaVo(menu.getName(), menu.getIcon(), menu.getActiveMenu());
                childMeta.setOrderNo(menu.getSort());
                children.setMeta(childMeta);
                childrenList.add(children);
                router.setChildren(childrenList);
            }
            routers.add(router);
        }
        return routers;
    }

    public boolean hasChildByMenuId(Long id) {
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id",id);
        long result = baseMapper.selectCount(queryWrapper);
        return result > 0 ? true : false;
    }

    public boolean checkMenuExistRole(Long id) {
        QueryWrapper<SysRoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("menu_id",id);
        long result = sysRoleMenuMapper.selectCount(queryWrapper);
        return result > 0 ? true : false;
    }

    public boolean checkMenuNameUnique(SysMenu menu) {
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        Long id = menu.getId()!=null ? menu.getId() : -1L;
        queryWrapper.eq("name",menu.getName());
        queryWrapper.eq("parent_id",menu.getParentId());
        SysMenu info = baseMapper.selectOne(queryWrapper);
        if (info != null && !info.getId().equals(id)) {
            return true;
        }
        return false;
    }

    public boolean checkMenuName(SysMenu menu) {
        return checkMenuNameUnique(menu);
    }

    public boolean checkMenuPathUnique(SysMenu menu) {
        QueryWrapper<SysMenu> queryWrapper = new QueryWrapper<>();
        Long id = menu.getId() != null ? menu.getId() : -1L;
        queryWrapper.eq("path", menu.getPath());
        SysMenu info = baseMapper.selectOne(queryWrapper);
        if (info != null && !info.getId().equals(id)) {
            return true;
        }
        return false;
    }

    public List<Long> selectIdListByRoleId(Map<String,Object> map){
        List<Long> idList = new ArrayList<>();
        List<SysMenu> menus = selectPermsByRoleId(map);
        for (SysMenu m : menus) {
            idList.add(m.getId());
        }
        return idList;
    }

    public List<Long> selectIdListByRoleId(Long roleId){
        Map<String,Object> map = new HashMap<>();
        map.put("id", roleId);
        return selectIdListByRoleId(map);
    }

    public String getRouterPath(SysMenu menu) {
        String routerPath = menu.getPath();
        if ("M".equals(menu.getType())) {
            if (!BaseConstant.PARENT_VIEW.equals(menu.getUrl())){
                routerPath = "/" + menu.getPath();
            }
        }else if ("C".equals(menu.getType()) && BaseConstant.PARENT_ID.equals(menu.getParentId())){
            routerPath = "/";
        }
        return routerPath;
    }

    /**
     * 角色-菜单绑定变更后失效该角色的权限/菜单缓存。
     * <p>不再写入无 TTL 的 {@code CACHE_ROLE_PERM_LIST} 哈希：旧实现先读 1h 缓存
     * 再写入永不失效的哈希，绑定变了权限也永远停在旧值。读取端统一走带 TTL
     * 的 {@code jffast:role:perms:{roleId}}。</p>
     */
    public void updateRolePerm(Long roleId){
        sysRoleMenuService.clearRoleCache(roleId);
    }

    /** 菜单写操作的操作者授权上下文（角色菜单并集 + 有效权限码集合） */
    public record MenuOperatorContext(Long operatorId, Set<Long> menuIds, Set<String> perms, boolean superAdmin) {
    }

    /**
     * 菜单写侧授权校验。
     *
     * <p><b>为什么需要它</b>：权限码不是独立配置，而是挂在菜单上的。任何角色绑定了某菜单，
     * 就按该菜单当前的 {@code perms} 获得权限。若允许持 {@code system:menu:update} 的低权管理员
     * 随意改写自己角色已绑定菜单的 {@code perms}，他就能把 {@code system:menu:update} 改成
     * {@code system:config:update} 并重新登录，凭空取得一个自己原本没有的权限码（自提权）。
     * 角色侧的 {@code validateRoleWrite} 只校验「分配的菜单是不是操作者已有的」，管不到这件事。</p>
     *
     * <p><b>规则（非超管）</b>：</p>
     * <ol>
     *   <li>只能维护自己角色已绑定的菜单（顶栏菜单由角色授权时补祖先，正常管理员不会缺）；</li>
     *   <li>改 {@code perms} 时，新权限码必须已经是操作者自己持有的权限码——不能凭空定义新权限；</li>
     *   <li>清空 {@code perms}（降权）、只改名称/排序/路径等非敏感字段、请求未携带 {@code perms}（null 表示不更新）均放行；</li>
     *   <li>新增菜单允许指定新 {@code perms}（未绑定角色前不生效，绑定要走角色授权，而角色授权规则 5 同样禁止分配未拥有的菜单），但只能挂在自己已有的菜单下。</li>
     * </ol>
     *
     * <p>超管不受限；这保证「正常维护菜单与权限码」不被破坏——定义新权限码是超管的职责。</p>
     */
    static void validateMenuWrite(SysMenu incoming, SysMenu existing, MenuOperatorContext operator) {
        if (incoming == null || operator == null) {
            throw forbidden("菜单写操作上下文缺失");
        }
        if (operator.superAdmin()) {
            return;
        }
        if (operator.operatorId() == null) {
            throw forbidden("操作者数据范围异常");
        }
        if (existing == null) {
            Long parentId = incoming.getParentId();
            if (parentId == null || BaseConstant.PARENT_ID.equals(parentId)) {
                throw forbidden("无权新建顶级菜单");
            }
            if (!operator.menuIds().contains(parentId)) {
                throw forbidden("无权在该菜单下新增子菜单");
            }
            return;
        }
        if (!operator.menuIds().contains(existing.getId())) {
            throw forbidden("无权维护未拥有的菜单");
        }
        if (incoming.getPerms() == null) {
            return;
        }
        String oldPerms = StrUtil.trimToEmpty(existing.getPerms());
        String newPerms = StrUtil.trimToEmpty(incoming.getPerms());
        if (Objects.equals(oldPerms, newPerms) || StrUtil.isBlank(newPerms)) {
            return;
        }
        if (!operator.perms().contains(newPerms)) {
            throw forbidden("无权定义未拥有的权限码");
        }
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), message);
    }

    /** 从登录态构建菜单写操作上下文；任一角色/菜单来源缺失都 fail-closed。 */
    private MenuOperatorContext loadMenuOperatorContext() {
        Long operatorId = LoginUtil.getLoginUserId();
        if (operatorId == null) {
            throw forbidden("操作者数据范围异常");
        }
        if (LoginUtil.isSuperAdmin()) {
            return new MenuOperatorContext(operatorId, Set.of(), Set.of(), true);
        }
        List<Long> roleIds = LoginUtil.getRoleIdList().stream().filter(Objects::nonNull).distinct().toList();
        if (roleIds.isEmpty()) {
            throw forbidden("操作者数据范围异常");
        }
        Set<Long> menuIds = new HashSet<>();
        Set<String> perms = new HashSet<>();
        List<SysMenu> roleMenus = selectMenuListByRoleIds(roleIds);
        for (SysMenu roleMenu : roleMenus) {
            if (roleMenu.getId() != null) {
                menuIds.add(roleMenu.getId());
            }
            if (StrUtil.isNotBlank(roleMenu.getPerms())) {
                perms.add(roleMenu.getPerms().trim());
            }
        }
        return new MenuOperatorContext(operatorId, menuIds, perms, false);
    }

    /** 新增菜单：先做写侧授权，再落库（落库仍走 {@link #save} 以清理权限缓存）。 */
    public boolean createMenu(SysMenu menu) {
        validateMenuWrite(menu, null, loadMenuOperatorContext());
        return save(menu);
    }

    /** 修改菜单：先做写侧授权，再落库（落库仍走 {@link #updateById} 以清理权限缓存）。 */
    public boolean updateMenu(SysMenu menu) {
        if (menu == null || menu.getId() == null) {
            throw new BusinessException("菜单不存在");
        }
        SysMenu existing = getById(menu.getId());
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        validateMenuWrite(menu, existing, loadMenuOperatorContext());
        return updateById(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public R changeMenuSort(SysMenuRequest menu){
        SysMenu menuA = this.getById(menu.getMenuIdA());
        SysMenu menuB = this.getById(menu.getMenuIdB());
        if (menuA == null || menuB == null) {
            return R.error("菜单不存在");
        }
        Integer sortA = menuA.getSort();
        menuA.setSort(menuB.getSort());
        menuB.setSort(sortA);
        this.updateById(menuA);
        this.updateById(menuB);
        return R.success();
    }

}
