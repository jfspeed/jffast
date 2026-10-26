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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.domain.dto.SysRoleMenuResponse;
import cn.jfspeed.system.entity.SysRoleMenu;
import cn.jfspeed.system.mapper.SysRoleMenuMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 角色菜单关联 服务实现*
 *
 * @author jffast
 */
@Slf4j
@Service
public class SysRoleMenuService extends ServiceImpl<SysRoleMenuMapper, SysRoleMenu> {

    private static final String CACHE_ROLE_PERMS = "jffast:role:perms:";
    private static final long CACHE_TTL = 3600;

    public int insertBatch(List<SysRoleMenu> list){
        return baseMapper.insertBatch(list);
    }

    public List<SysRoleMenu> listAll(){
        return baseMapper.listAll();
    }

    /**
     * 获取角色按钮权限（带缓存）
     */
    public List<String> listAllBtnPerm(Long roleId){
        String cacheKey = CACHE_ROLE_PERMS + roleId;
        Object cached = RedisUtil.get(cacheKey);
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> cachedPerms = (List<String>) cached;
            return cachedPerms;
        }

        List<SysRoleMenuResponse> sysRoleMenuDTOS = baseMapper.listAllBtnPerm(roleId);
        List<String> perms = sysRoleMenuDTOS.stream()
                .map(SysRoleMenuResponse::getPerms)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        RedisUtil.set(cacheKey, perms, CACHE_TTL);
        return perms;
    }

    public List<Long> listAllMenuIdList(Long roleId){
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId, roleId);
        List<SysRoleMenu> sysRoleMenus = baseMapper.selectList(wrapper);

        List<Long> menuIds = sysRoleMenus.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());

        return menuIds;
    }

    /**
     * 清除角色权限缓存：TTL 缓存键 + 历史无 TTL 哈希字段 + 全量菜单 ID 哈希字段。
     * <p>历史版本把权限写进无 TTL 的 hash，角色绑定变更后旧值永久命中；
     * 这里一并删除，保证升级后旧缓存自然失效。</p>
     */
    public void clearRoleCache(Long roleId) {
        if (roleId == null) {
            return;
        }
        RedisUtil.del(CACHE_ROLE_PERMS + roleId);
        RedisUtil.hdel(BaseConstant.CACHE_ROLE_PERM_LIST, String.valueOf(roleId));
        RedisUtil.hdel(BaseConstant.CACHE_ALL_MENU_ID_LIST, String.valueOf(roleId));
    }

    /** 兼容旧调用名 */
    public void clearRolePermsCache(Long roleId) {
        clearRoleCache(roleId);
    }

    /**
     * 清除所有角色权限缓存（菜单定义变更时调用）
     */
    public void clearAllRoleCache() {
        String[] keys = RedisUtil.allKey(CACHE_ROLE_PERMS + "*");
        if (keys != null && keys.length > 0) {
            RedisUtil.del(keys);
        }
        RedisUtil.del(BaseConstant.CACHE_ROLE_PERM_LIST);
        RedisUtil.del(BaseConstant.CACHE_ALL_MENU_ID_LIST);
    }

    public void clearAllRolePermsCache() {
        clearAllRoleCache();
    }

    @Override
    public boolean save(SysRoleMenu entity) {
        boolean result = super.save(entity);
        if (result && entity.getRoleId() != null) {
            clearRoleCache(entity.getRoleId());
        }
        return result;
    }

    @Override
    public boolean remove(Wrapper<SysRoleMenu> queryWrapper) {
        // 先取出受影响角色，删除后逐个失效；覆盖 remove(wrapper) 这条批量删除路径
        List<Long> roleIds = list(queryWrapper).stream()
                .map(SysRoleMenu::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        boolean result = super.remove(queryWrapper);
        if (result) {
            roleIds.forEach(this::clearRoleCache);
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        SysRoleMenu roleMenu = getById(id);
        boolean result = super.removeById(id);
        if (result && roleMenu != null && roleMenu.getRoleId() != null) {
            clearRoleCache(roleMenu.getRoleId());
        }
        return result;
    }
}
