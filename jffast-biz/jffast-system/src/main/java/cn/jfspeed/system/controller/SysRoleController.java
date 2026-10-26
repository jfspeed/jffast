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


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.jfspeed.common.core.util.IdUtils;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.system.util.PinyinCodeUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.system.domain.dto.SysRoleRequest;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.service.SysRoleService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.web.domain.dto.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Map;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/role")
@Tag(name = "角色管理")
public class SysRoleController extends BaseController<SysRoleService,SysRole> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:view")
    @Operation(summary = "列表")
    public R<PageRes<SysRole>> list(@RequestParam Map<String,String> map, @ParameterObject PageReq pageReq) {
        return R.success(service.page(map,pageReq));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:view")
    @Operation(summary = "根据id获取信息")
    public R getById(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "生成角色代码")
    @GetMapping("/createDictCode")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:view")
    @Operation(summary = "生成角色代码")
    public R createDictCode(String dictName) {
        // 调用方统一传 dictName（角色/字典共用该「生成代号」端点）；参数名若写成
        // roleName，Spring 永远绑不上 -> 历史 NPE 500。拼音/兜底逻辑见 PinyinCodeUtil。
        return R.success(PinyinCodeUtil.firstLetterCode(dictName));
    }

    @SLog(title = "新增角色")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增角色")
    public R insert(@RequestBody @Valid SysRoleRequest req) {
        SysRole role = BeanUtil.copyProperties(req, SysRole.class);
        // 先落新 ID 再查重：排除自身时不会误排除掉客户端传入的同名角色 ID
        role.setId(IdUtils.snowflakeShort());
        String conflict = service.checkRoleNameAndKey(role.getRoleName(), role.getRoleKey(), role.getId());
        if (conflict != null) {
            return R.error(conflict);
        }
        SysRoleService.EffectiveRole effective = service.resolveEffectiveRole(req, null);
        service.validateRoleWrite(req, null, effective);
        role.setRoleKey(effective.roleKey());
        role.setDataScope(effective.dataScope());
        service.createRole(role);
        return R.success();
    }

    @SLog(title = "修改角色")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:update")
    @DistributedLock(key = "'role:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改角色")
    public R update(@RequestBody @Valid SysRoleRequest req) {
        if (req.getId() == null) {
            return R.error("角色不存在");
        }
        SysRole existing = service.getById(req.getId());
        if (existing == null) {
            return R.error("角色不存在");
        }
        SysRole role = BeanUtil.copyProperties(req, SysRole.class);
        String conflict = service.checkRoleNameAndKey(role.getRoleName(), role.getRoleKey(), role.getId());
        if (conflict != null) {
            return R.error(conflict);
        }
        SysRoleService.EffectiveRole effective = service.resolveEffectiveRole(req, existing);
        service.validateRoleWrite(req, existing, effective);
        role.setRoleKey(effective.roleKey());
        role.setDataScope(effective.dataScope());
        service.updateRole(role, req.getMenuIds() != null, req.getDeptIds() != null);
        return R.success();
    }

    @SLog(title = "删除角色")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:delete")
    @Operation(summary = "删除角色")
    public R delete(@PathVariable("id") Long [] id) {
        String error = service.deleteRoles(id);
        return error == null ? R.success() : R.error(error);
    }

    @GetMapping("/roleSelect")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:role:view")
    @Operation(summary = "角色下拉框")
    public R roleSelect() {
        QueryWrapper<SysRole> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("create_time");
        return R.success(service.list(queryWrapper));
    }
}
