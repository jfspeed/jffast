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
import cn.hutool.core.bean.BeanUtil;
import jakarta.validation.Valid;
import cn.jfspeed.system.domain.dto.SysNotificationTemplateGroupRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.system.entity.SysNotificationTemplateGroup;
import cn.jfspeed.system.service.SysNotificationTemplateGroupService;
import cn.jfspeed.security.annotation.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;

/**
 * 通知模板分组
 *
 * @author jffast
 */
@Tag(name = "通知模板分组")
@RestController
@RequestMapping("/sys/notification/template/group")
@ConditionalOnProperty(name = "jffast.system.notification.enabled", havingValue = "true")
public class SysNotificationTemplateGroupController {

    @Resource
    private SysNotificationTemplateGroupService groupService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:view")
    @Operation(summary = "分页查询分组")
    @GetMapping("/page")
    public R<PageRes<SysNotificationTemplateGroup>> page(SysNotificationTemplateGroup param, PageReq pageReq) {
        return R.success(groupService.page(param, pageReq));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:view")
    @Operation(summary = "查询所有启用分组")
    @GetMapping("/list")
    public R<List<SysNotificationTemplateGroup>> list() {
        return R.success(groupService.listEnabled());
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:add")
    @SLog(title = "新增通知模板分组")
    @Idempotent(expire = 5)
    @Operation(summary = "新增分组")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid SysNotificationTemplateGroupRequest req) {
        SysNotificationTemplateGroup entity = BeanUtil.copyProperties(req, SysNotificationTemplateGroup.class);
        groupService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:edit")
    @SLog(title = "修改通知模板分组")
    @Idempotent(expire = 5)
    @Operation(summary = "修改分组")
    @PutMapping("/update")
    public R<Void> update(@RequestBody @Valid SysNotificationTemplateGroupRequest req) {
        SysNotificationTemplateGroup entity = BeanUtil.copyProperties(req, SysNotificationTemplateGroup.class);
        groupService.updateById(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:delete")
    @SLog(title = "删除通知模板分组")
    @Operation(summary = "删除分组")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        groupService.removeById(id);
        return R.success();
    }
}
