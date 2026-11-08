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
import java.util.Objects;

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
import cn.jfspeed.system.domain.dto.SysDeptRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.TreeUtil;
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysDeptService;
import cn.jfspeed.system.service.SysUserScopeQuery;
import cn.jfspeed.system.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/dept")
@Tag(name = "部门管理")
public class SysDeptController extends BaseController<SysDeptService,SysDept> {

    @Resource
    private SysUserService sysUserService;

    /** 受数据权限约束的部门存在性查询（独立 Bean，保证 @DataPermission 切面生效） */
    @Resource
    private SysDeptScopeQuery deptScopeQuery;

    /** 受数据权限约束的用户存在性查询，用于 userDeptTree 的越权枚举防护 */
    @Resource
    private SysUserScopeQuery userScopeQuery;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "部门树列表")
    public R list(@RequestParam Map<String,Object> map) {
        List<SysDept> list = service.list(map);
        return R.success(TreeUtil.buildTreeIsRoot(list,BaseConstant.PARENT_ID));
    }

    @GetMapping("/all")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "获取全部部门")
    public R<List<SysDept>> all() {
        return R.success(service.list(new HashMap<>()));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "根据id获取信息")
    public R getById(@PathVariable("id") Long id) {
        SysDept dept = deptScopeQuery.findVisibleDept(id);
        if (dept == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "部门不存在或无权操作");
        }
        return R.success(dept);
    }

    @GetMapping(value = "/roleDeptTree/{roleId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "加载对应角色部门列表树")
    public R roleDeptTree(@PathVariable("roleId") Long roleId) {
        Map<String,Object> params = new HashMap<>();
        List<SysDept> deptList = service.list(params);
        Map<String,Object> map = new HashMap<>();
        map.put("deptList", TreeUtil.buildTreeIsRoot(deptList,BaseConstant.PARENT_ID));
        params.put("id",roleId);
        map.put("checkedKeys", service.selectIdListByRoleId(params));
        return R.success(map);
    }
    @GetMapping(value = "/userDeptTree/{userId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "加载对应用户部门列表树")
    public R userDeptTree(@PathVariable("userId") Long userId) {
        // 用户可见性必须受数据权限约束：否则任意低权用户可把 userId → deptId 批量映射出来
        if (userScopeQuery.findVisibleUser(userId) == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "用户不存在或无权操作");
        }
        Map<String,Object> params = new HashMap<>();
        List<SysDept> deptList = service.list(params);
        Map<String,Object> map = new HashMap<>();
        map.put("deptList", TreeUtil.buildTreeIsRoot(deptList,BaseConstant.PARENT_ID));
        params.put("id",userId);
        SysUser user = sysUserService.getById(userId);
        Long deptId = null;
        if (user != null) {
            deptId = user.getDeptId();
        }

        map.put("checkedKeys", deptId != null ? String.valueOf(deptId) : "");
        return R.success(map);
    }

    @SLog(title = "新增部门")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增部门")
    public R insert(@RequestBody @Valid SysDeptRequest req) {
        SysDept dept = BeanUtil.copyProperties(req, SysDept.class);
        Long parentId = dept.getParentId() == null ? BaseConstant.PARENT_ID : dept.getParentId();
        if (BaseConstant.PARENT_ID.equals(parentId)) {
            // 顶级部门会改变整棵组织树与数据范围基线，仅超管可建
            if (!LoginUtil.isSuperAdmin()) {
                return R.error(AuthErrorCode.FORBIDDEN.getCode(), "无权新建顶级部门");
            }
        } else if (deptScopeQuery.findVisibleDept(parentId) == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "父级部门不存在或无权操作");
        }
        if (service.checkDeptName(dept)) {
            return R.error("名称已存在");
        }
        dept.setId(IdUtils.snowflakeShort());
        service.createDept(dept);
        service.save(dept);
        return R.success();
    }

    @SLog(title = "修改部门")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:update")
    @DistributedLock(key = "'dept:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改部门")
    public R update(@RequestBody @Valid SysDeptRequest req) {
        SysDept dept = BeanUtil.copyProperties(req, SysDept.class);
        if (dept.getId()==null){
            return R.error("部门 id 不能为空");
        }
        // 先做范围校验，再查名称：避免用「名称已存在」当存在性探针去试探范围外部门
        SysDept existing = deptScopeQuery.findVisibleDept(dept.getId());
        if (existing == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "部门不存在或无权操作");
        }
        Long parentId = dept.getParentId();
        if (parentId != null) {
            Long currentParentId = existing.getParentId() == null ? BaseConstant.PARENT_ID : existing.getParentId();
            // 只有「上级真的变了」才需要新上级可见：上级不变是日常改名，不能因为
            // 上级不在本人的数据范围内就把合法改名也拒掉（scope=本部门时上级天然不可见）
            if (!Objects.equals(parentId, currentParentId)) {
                if (BaseConstant.PARENT_ID.equals(parentId)) {
                    // 挂到根级会改变整棵组织树与数据范围基线，仅超管可做
                    if (!LoginUtil.isSuperAdmin()) {
                        return R.error(AuthErrorCode.FORBIDDEN.getCode(), "无权将部门挂到根级");
                    }
                } else if (deptScopeQuery.findVisibleDept(parentId) == null) {
                    return R.error(AuthErrorCode.FORBIDDEN.getCode(), "父级部门不存在或无权操作");
                }
            }
        }
        if (service.checkDeptName(dept)) {
            return R.error("名称已存在");
        }
        service.updateDept(dept);
        return R.success();
    }

    @SLog(title = "删除部门")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:delete")
    @Operation(summary = "删除部门")
    public R delete(@PathVariable("id") Long id) {
        if (deptScopeQuery.findVisibleDept(id) == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "部门不存在或无权操作");
        }
        if (service.hasChildByDeptId(id)) {
            return R.error("存在下级,不允许删除");
        }
        if (service.checkDeptExistUser(id)) {
            return R.error("该组织存在用户，不允许删除");
        }
        if (service.checkDeptExistRole(id)) {
            return R.error("该组织已分配了角色，不允许删除");
        }
        service.removeById(id);
        return R.success();
    }

    @GetMapping("/deptUserTree")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "部门用户树（部门+人员）")
    public R deptUserTree() {
        return R.success(service.deptUserTree());
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:dept:view")
    @Operation(summary = "部门用户统计")
    @GetMapping("/userCount")
    public R<Map<String, Object>> userCount() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalDepts", service.count());
        result.put("totalUsers", sysUserService.count());
        return R.success(result);
    }
}
