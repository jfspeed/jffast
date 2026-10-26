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

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysMenu;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysRoleDept;
import cn.jfspeed.system.entity.SysRoleMenu;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.event.RoleAuthorizationChangedEvent;
import cn.jfspeed.system.mapper.SysRoleMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色信息服务实现类
 *
 * @author jffast
 */
@Slf4j
@Service
public class SysRoleService extends BaseService<SysRoleMapper, SysRole>{

    private static final int DATA_SCOPE_ALL = 1;
    private static final int DATA_SCOPE_CUSTOM_DEPT = 5;
    /** 与 {@code sql/system_sql.sql} 的 `uk_sys_role_tenant_role_key` 同名，仅用于内部区分撞的是哪个唯一约束。 */
    private static final String ROLE_KEY_UNIQUE_INDEX = "uk_sys_role_tenant_role_key";

    @Resource
    private SysMenuService sysMenuService;
    @Resource
    private SysRoleMenuService sysRoleMenuService;
    @Resource
    private SysRoleDeptService sysRoleDeptService;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /** 有效角色定义：校验与落库共用同一份值，服务层不得再按 roleKey 重算 */
    public record EffectiveRole(String roleKey, Integer dataScope) {
    }

    /** 操作者授权上下文：角色/菜单/秩全部取自 DB 权威值，缺失即拒绝 */
    public record OperatorContext(Long operatorId, List<Long> roleIds, int rank,
                                  Set<Long> menuIds, boolean superAdmin) {
    }

    /**
     * 角色分页列表
     */
    public PageRes<SysRole> page(Map<String,String> query, PageReq pageReq) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.get("roleName")),SysRole::getRoleName, query.get("roleName"));
        queryWrapper.like(StrUtil.isNotBlank(query.get("roleKey")),SysRole::getRoleKey, query.get("roleKey"));
        queryWrapper.orderByDesc(SysRole::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(),queryWrapper));
    }

    /**
     * 获取角色id，根据角色名称
     */
    public Long getIdByRoleName(String roleName){
        QueryWrapper<SysRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_name", roleName);
        SysRole role = baseMapper.selectOne(queryWrapper);
        if (role != null){
            return role.getId();
        }
        return null;
    }

    /**
     * 根据多个id查询角色
     */
    public List<SysRole> getByIds(List<Long> ids){
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        QueryWrapper<SysRole> query = new QueryWrapper<>();
        query.in("id", ids);
        query.select("id,role_key,role_name,data_scope");
        return baseMapper.selectList(query);
    }

    /**
     * 生成id（数字，从小到大生成）
     * 普通用户角色id默认是最大的
     */
    public Long createId(){
        // 先将普通用户角色的id查出来
        QueryWrapper<SysRole> query = new QueryWrapper<>();
        query.eq("role_key", BaseConstant.ROLE_COMMON);
        SysRole commonRole = baseMapper.selectOne(query);
        if (commonRole == null) {
            throw new BusinessException("普通用户角色不存在，请检查初始化数据");
        }
        Long ptyhId = commonRole.getId();
        QueryWrapper<SysRole> query1 = new QueryWrapper<>();
        query1.ne("id",ptyhId); // 排除普通用户
        query1.select("id");
        query1.orderByDesc("id"); // id倒序排序
        query1.last("limit 1"); // 取id最大的那一条
        SysRole maxRole = baseMapper.selectOne(query1);
        Long id = maxRole != null ? maxRole.getId() : ptyhId;
        return id + 1;
    }

    /**
     * 校验角色名称和角色代码是否唯一
     * @return 返回冲突信息，null表示无冲突
     */
    public String checkRoleNameAndKey(String roleName, String roleKey,Long id) {
        if (StrUtil.isBlank(roleName) && StrUtil.isBlank(roleKey)) {
            return null;
        }
        QueryWrapper<SysRole> queryWrapper = new QueryWrapper<>();
        if(StrUtil.isNotBlank(roleName) && StrUtil.isNotBlank(roleKey)) {
            queryWrapper.and(w -> w.eq("role_name", roleName).or().eq("role_key", roleKey));
        } else if (StrUtil.isNotBlank(roleName)) {
            queryWrapper.eq("role_name", roleName);
        } else {
            queryWrapper.eq("role_key", roleKey);
        }
        if (Objects.nonNull(id)){
            queryWrapper.ne("id", id);
        }
        List<SysRole> existList = baseMapper.selectList(queryWrapper);
        if (existList.isEmpty()) {
            return null;
        }
        for (SysRole r : existList) {
            if (roleName != null && roleName.equals(r.getRoleName())) {
                return "角色名称已存在";
            }
            if (roleKey != null && roleKey.equals(r.getRoleKey())) {
                return "角色代码已存在";
            }
        }
        return null;
    }

    /**
     * 新增角色菜单信息
     */
    public void insertRoleMenu(Long id, Long[] menuIds) {
        QueryWrapper<SysRoleMenu> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysRoleMenu::getRoleId,id);
        sysRoleMenuService.remove(queryWrapper);
        if (ArrayUtil.isNotEmpty(menuIds)) {
            Long[] fullMenuIds = expandMenuAncestors(menuIds);
            List<SysRoleMenu> list = new ArrayList<>();
            for (Long menuId : fullMenuIds) {
                list.add(new SysRoleMenu(id,menuId));
            }
            if (!list.isEmpty()){
                sysRoleMenuService.insertBatch(list);
            }
        }
        // 绑定已变更：立即失效该角色权限缓存（含历史无 TTL 哈希字段）
        sysRoleMenuService.clearRoleCache(id);
    }

    /**
     * 补全菜单祖先（授权校验与写入共用同一函数，避免口径不同导致子集判定失真）
     */
    public Long[] expandMenuAncestors(Long[] menuIds) {
        Set<Long> result = new LinkedHashSet<>();
        if (menuIds == null) {
            return new Long[0];
        }
        for (Long menuId : menuIds) {
            if (menuId != null) {
                result.add(menuId);
            }
        }
        for (Long menuId : menuIds) {
            Long currentId = menuId;
            while (currentId != null && !BaseConstant.PARENT_ID.equals(currentId)) {
                SysMenu menu = sysMenuService.getById(currentId);
                if (menu == null || menu.getParentId() == null || BaseConstant.PARENT_ID.equals(menu.getParentId())) {
                    break;
                }
                result.add(menu.getParentId());
                currentId = menu.getParentId();
            }
        }
        return result.toArray(new Long[0]);
    }

    /**
     * 新增角色部门信息：{@code null} 必须是 no-op（保持既有绑定），不得归一为 {@code []} 后删除
     */
    public void insertRoleDept(Long id, Long[] deptIds) {
        if (deptIds == null) {
            log.warn("deptIds=null，保持角色 {} 既有部门绑定", id);
            return;
        }
        // 删除角色与部门关联
        QueryWrapper<SysRoleDept> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysRoleDept::getRoleId,id);
        sysRoleDeptService.remove(queryWrapper);
        List<SysRoleDept> list = new ArrayList<>();
        // 新增角色与部门关联：先去重，重复 deptId 会撞 (role_id, dept_id) 主键
        for (Long deptId : new LinkedHashSet<>(Arrays.asList(deptIds))) {
            if (deptId != null) {
                list.add(new SysRoleDept(id,deptId));
            }
        }
        if (!list.isEmpty()){
            sysRoleDeptService.insertBatch(list);
        }
    }

    /**
     * 有效角色定义唯一计算入口：创建按 roleKey 默认映射；更新优先沿用库中原值。
     */
    public EffectiveRole resolveEffectiveRole(SysRoleRequest req, SysRole existing) {
        String roleKey = StrUtil.isNotBlank(req.getRoleKey()) ? req.getRoleKey()
                : (existing == null ? null : existing.getRoleKey());
        if (StrUtil.isBlank(roleKey)) {
            throw new BusinessException("角色标识不能为空");
        }
        Integer dataScope = req.getDataScope();
        if (dataScope == null && existing != null) {
            dataScope = existing.getDataScope();
        }
        if (dataScope == null) {
            dataScope = defaultDataScope(roleKey);
        }
        return new EffectiveRole(roleKey, dataScope);
    }

    static int defaultDataScope(String roleKey) {
        if (BaseConstant.ROLE_SUPER_ADMIN.equals(roleKey)) {
            return DATA_SCOPE_ALL;
        }
        if (BaseConstant.ROLE_ADMIN.equals(roleKey)) {
            return 2;
        }
        if (BaseConstant.ROLE_COMMON.equals(roleKey)) {
            return 4;
        }
        return 3;
    }

    /** 数据范围偏序：1 > 2/3/5 > 4 > (缺失) */
    public static int roleRank(Integer dataScope) {
        if (dataScope == null) {
            return 0;
        }
        return switch (dataScope) {
            case DATA_SCOPE_ALL -> 3;
            case 2, 3, DATA_SCOPE_CUSTOM_DEPT -> 2;
            case 4 -> 1;
            default -> 0;
        };
    }

    /**
     * 构建操作者授权上下文：角色按 ID 精确查询，任一缺失即 403；菜单取并集后补祖先。
     */
    public OperatorContext loadOperatorContext() {
        Long operatorId = LoginUtil.getLoginUserId();
        if (operatorId == null) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "操作者数据范围异常");
        }
        if (LoginUtil.isSuperAdmin()) {
            return new OperatorContext(operatorId, new ArrayList<>(), Integer.MAX_VALUE, new HashSet<>(), true);
        }
        List<Long> roleIds = LoginUtil.getRoleIdList().stream().filter(Objects::nonNull).distinct().toList();
        if (roleIds.isEmpty()) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "操作者数据范围异常");
        }
        List<SysRole> roles = listByIds(roleIds);
        if (roles == null || roles.size() != roleIds.size()) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "操作者数据范围异常");
        }
        int rank = roles.stream().mapToInt(role -> roleRank(role.getDataScope())).max().orElse(0);
        Set<Long> menuIds = new HashSet<>();
        for (Long roleId : roleIds) {
            List<Long> ids = sysRoleMenuService.listAllMenuIdList(roleId);
            if (ids != null) {
                menuIds.addAll(ids);
            }
        }
        Long[] expanded = expandMenuAncestors(menuIds.toArray(new Long[0]));
        return new OperatorContext(operatorId, roleIds, rank, new HashSet<>(Arrays.asList(expanded)), false);
    }

    /**
     * 角色定义写侧授权：只使用解析后的有效值，禁止按请求原值或会话缓存判定。
     */
    public void validateRoleWrite(SysRoleRequest req, SysRole existing, EffectiveRole effective) {
        validateRoleWrite(req, existing, effective, loadOperatorContext());
    }

    void validateRoleWrite(SysRoleRequest req, SysRole existing, EffectiveRole effective,
                           OperatorContext operator) {
        if (effective == null || StrUtil.isBlank(effective.roleKey()) || effective.dataScope() == null) {
            throw new BusinessException("角色定义数据不完整");
        }
        Long roleId = existing == null ? null : existing.getId();
        boolean superAdmin = operator.superAdmin();

        // 规则 1：内置超管角色仅超管可改，且 roleKey/dataScope 不可变
        if (BaseConstant.ROLE_ID_SUPER_ADMIN_LONG.equals(roleId)) {
            if (!superAdmin
                    || !Objects.equals(effective.roleKey(), existing.getRoleKey())
                    || !Objects.equals(effective.dataScope(), existing.getDataScope())) {
                throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "内置超管角色不允许删除/修改标识");
            }
        }

        // 规则 2：内置 roleKey 在已有角色上不可改名；非超管创建/改名不得使用内置 roleKey
        boolean renamed = existing == null || !Objects.equals(existing.getRoleKey(), effective.roleKey());
        if (existing != null && isBuiltInRoleKey(existing.getRoleKey()) && renamed) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "内置角色标识不允许修改");
        }
        if (!superAdmin && renamed && isBuiltInRoleKey(effective.roleKey())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权使用内置角色标识");
        }

        if (superAdmin) {
            return;
        }

        // 规则 3：非超管不得设置全部数据/自定义部门范围
        if (effective.dataScope() == DATA_SCOPE_ALL || effective.dataScope() == DATA_SCOPE_CUSTOM_DEPT) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权设置该数据范围");
        }

        // 规则 4：非超管秩必须 >0 且不得高于操作者
        if (operator.rank() == 0) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "操作者数据范围异常");
        }
        if (roleRank(effective.dataScope()) > operator.rank()) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权设置高于自己的数据范围");
        }

        // 规则 5：请求的菜单（含祖先）必须是操作者已有菜单的子集
        if (req.getMenuIds() != null) {
            Set<Long> requested = Arrays.stream(expandMenuAncestors(req.getMenuIds()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!operator.menuIds().containsAll(requested)) {
                throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权分配未拥有的菜单或按钮");
            }
        }

        // 规则 6：非超管不得修改自己所属的角色
        if (roleId != null && hasUserRole(operator.operatorId(), roleId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "不能修改自己所属的角色");
        }

        // 规则 7：非超管不得操作 dataScope=1 的角色
        if (existing != null && Integer.valueOf(DATA_SCOPE_ALL).equals(existing.getDataScope())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权操作该角色");
        }
    }

    /**
     * 唯一索引冲突的可读提示。控制器的 {@code checkRoleNameAndKey} 是「先查后插」，并发下两个
     * 请求能双双通过，且 roleKey 在查重之后还会被 {@code resolveEffectiveRole} 改写，
     * 真正的兜底是库侧 uk_sys_role_tenant_role_key；冲突在此翻译成本句，避免前端收到 500 与 SQL 原文。
     *
     * <p>{@code sys_role} 上可能撞的唯一约束有两个（主键 id、role_key 组合键），提示必须分开——
     * 把 id 冲突报成「标识已被占用」会把人往错的方向查。索引名只用于内部分支判断，不外传。
     */
    private BusinessException duplicateKeyFailure(SysRole role, DuplicateKeyException e) {
        String detail = duplicateKeyDetail(e);
        String message = detail.contains(ROLE_KEY_UNIQUE_INDEX)
                ? "角色标识已被占用：" + role.getRoleKey()
                : "角色 ID 冲突，请刷新后重试";
        return new BusinessException(FrameworkErrorCode.CONFLICT.getCode(), message);
    }

    /** 沿 cause 链收集驱动/SQL 异常原文，MySQL 的 "Duplicate entry ... for key ..." 可能只出现在根因里。 */
    private static String duplicateKeyDetail(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable t = e;
        for (int depth = 0; t != null && depth < 8; depth++, t = t.getCause()) {
            sb.append(t.getMessage()).append(' ');
        }
        return sb.toString();
    }

    /**
     * 创建角色：新角色不存在既有绑定，menuIds null/[] 等价空；不发布事件、不踢人。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createRole(SysRole role) {
        requireResolved(role);
        if (role.getId() == null) {
            throw new BusinessException("角色 ID 不能为空");
        }
        try {
            this.save(role);
        } catch (DuplicateKeyException e) {
            throw duplicateKeyFailure(role, e);
        }
        insertRoleMenu(role.getId(), role.getMenuIds());
        if (role.getDataScope() == DATA_SCOPE_CUSTOM_DEPT && ArrayUtil.isNotEmpty(role.getDeptIds())) {
            insertRoleDept(role.getId(), role.getDeptIds());
        }
        sysMenuService.updateRolePerm(role.getId());
    }

    /**
     * 更新角色：menuIds/deptIds 的 null=不修改、[]=清空、非空=替换；
     * 仅授权字段变化时在事务内发布事件，由 AFTER_COMMIT 监听器撤销绑定用户会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRole role, boolean syncMenus, boolean syncDepts) {
        if (role == null || role.getId() == null) {
            throw new BusinessException("角色不存在");
        }
        requireResolved(role);
        SysRole existing = this.getById(role.getId());
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        boolean roleKeyChanged = !Objects.equals(role.getRoleKey(), existing.getRoleKey());
        boolean dataScopeChanged = !Objects.equals(role.getDataScope(), existing.getDataScope());
        boolean deptBindingsChanged = role.getDataScope() == DATA_SCOPE_CUSTOM_DEPT
                && syncDepts && role.getDeptIds() != null
                && hasDeptBindingsChanged(role.getId(), role.getDeptIds());

        boolean updated;
        try {
            updated = this.updateById(role);
        } catch (DuplicateKeyException e) {
            throw duplicateKeyFailure(role, e);
        }
        if (!updated) {
            throw new BusinessException("角色不存在");
        }

        if (syncMenus) {
            if (role.getMenuIds() == null) {
                // 防御分支：syncMenus=true 但未携带 menuIds 时保持既有绑定，不得清空
                log.warn("syncMenus=true 但 menuIds=null，保持角色 {} 既有菜单绑定", role.getId());
            } else {
                insertRoleMenu(role.getId(), role.getMenuIds());
            }
        }

        if (role.getDataScope() == DATA_SCOPE_CUSTOM_DEPT) {
            if (!syncDepts) {
                // deptIds=null：保持既有部门绑定
            } else if (role.getDeptIds() == null) {
                // 防御分支：保持既有绑定，不得归一为 [] 后清空
                log.warn("syncDepts=true 但 deptIds=null，保持角色 {} 既有部门绑定", role.getId());
            } else {
                insertRoleDept(role.getId(), role.getDeptIds());
            }
        } else {
            // 非自定义数据范围：清理部门绑定（不单独踢人，dataScope 变化已由上方比较覆盖）
            QueryWrapper<SysRoleDept> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(SysRoleDept::getRoleId, role.getId());
            sysRoleDeptService.remove(queryWrapper);
        }
        sysMenuService.updateRolePerm(role.getId());

        if (roleKeyChanged || dataScopeChanged || deptBindingsChanged) {
            eventPublisher.publishEvent(new RoleAuthorizationChangedEvent(role.getId(), true));
        }
    }

    private void requireResolved(SysRole role) {
        if (role == null || StrUtil.isBlank(role.getRoleKey()) || role.getDataScope() == null) {
            throw new BusinessException("角色定义数据不完整");
        }
    }

    private boolean hasDeptBindingsChanged(Long roleId, Long[] requestedDeptIds) {
        Set<Long> current = sysRoleDeptService.list(new LambdaQueryWrapper<SysRoleDept>()
                        .eq(SysRoleDept::getRoleId, roleId))
                .stream().map(SysRoleDept::getDeptId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> requested = Arrays.stream(requestedDeptIds).filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return !current.equals(requested);
    }

    private boolean hasUserRole(Long userId, Long roleId) {
        QueryWrapper<SysUserRole> query = new QueryWrapper<>();
        query.eq("user_id", userId).eq("role_id", roleId);
        return sysUserRoleMapper.selectCount(query) > 0;
    }

    private static boolean isBuiltInRoleKey(String roleKey) {
        return BaseConstant.ROLE_SUPER_ADMIN.equals(roleKey)
                || BaseConstant.ROLE_ADMIN.equals(roleKey)
                || BaseConstant.ROLE_COMMON.equals(roleKey);
    }

    /**
     * 批量删除角色：先整体校验（存在性/超管/占用/支配），再统一清理。
     * 事务覆盖角色、角色-菜单、角色-部门三张表，避免删一半留下脏绑定。
     *
     * @return 冲突信息，null 表示删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public String deleteRoles(Long[] roleIds) {
        return deleteRoles(roleIds, loadOperatorContext());
    }

    String deleteRoles(Long[] roleIds, OperatorContext operator) {
        if (roleIds == null || roleIds.length == 0) {
            return "请选择要删除的角色";
        }
        for (Long roleId : roleIds) {
            SysRole role = this.getById(roleId);
            if (role == null) {
                return "角色不存在";
            }
            if (BaseConstant.ROLE_ID_SUPER_ADMIN_LONG.equals(roleId)) {
                return "超级管理员角色禁止删除";
            }
            if (!operator.superAdmin()) {
                // 规则 6/7：不得删除自己所属的角色或全部数据范围角色
                if (hasUserRole(operator.operatorId(), roleId)) {
                    return "不能修改自己所属的角色";
                }
                if (Integer.valueOf(DATA_SCOPE_ALL).equals(role.getDataScope())) {
                    return "无权操作该角色";
                }
            }
            // 规则 8：双表并查，任一存在绑定即拒绝
            QueryWrapper<SysUserRole> userRoleQuery = new QueryWrapper<>();
            userRoleQuery.eq("role_id", roleId);
            if (sysUserRoleMapper.selectCount(userRoleQuery) > 0) {
                return "角色已分配用户，不能删除";
            }
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.apply("FIND_IN_SET({0},role_id)", roleId);
            if (sysUserMapper.selectCount(queryWrapper) > 0) {
                return "角色已分配用户，不能删除";
            }
        }
        for (Long roleId : roleIds) {
            this.removeById(roleId);
            this.insertRoleMenu(roleId, new Long[0]);
            sysRoleDeptService.remove(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
            sysRoleMenuService.clearRoleCache(roleId);
        }
        return null;
    }
}
