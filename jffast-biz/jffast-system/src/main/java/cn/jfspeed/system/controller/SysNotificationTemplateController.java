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
import cn.jfspeed.system.domain.dto.SysNotificationTemplateRequest;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.system.entity.SysNotificationTemplate;
import cn.jfspeed.system.service.SysNotificationTemplateService;
import cn.jfspeed.security.annotation.Idempotent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

/**
 * 通知模板
 *
 * @author jffast
 */
@Tag(name = "通知模板")
@RestController
@RequestMapping("/sys/notification/template")
@ConditionalOnProperty(name = "jffast.system.notification.enabled", havingValue = "true")
public class SysNotificationTemplateController {

    @Resource
    private SysNotificationTemplateService templateService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:view")
    @Operation(summary = "分页查询模板")
    @GetMapping("/page")
    public R<PageRes<SysNotificationTemplate>> page(SysNotificationTemplate param, PageReq pageReq) {
        return R.success(templateService.page(param, pageReq));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:view")
    @Operation(summary = "模板详情")
    @GetMapping("/detail/{id}")
    public R<SysNotificationTemplate> detail(@PathVariable Long id) {
        return R.success(templateService.getById(id));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:add")
    @SLog(title = "新增通知模板")
    @Idempotent(expire = 5)
    @Operation(summary = "新增模板")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid SysNotificationTemplateRequest req) {
        SysNotificationTemplate entity = BeanUtil.copyProperties(req, SysNotificationTemplate.class);
        templateService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:edit")
    @SLog(title = "修改通知模板")
    @Idempotent(expire = 5)
    @Operation(summary = "修改模板")
    @PutMapping("/update")
    public R<Void> update(@RequestBody @Valid SysNotificationTemplateRequest req) {
        SysNotificationTemplate entity = BeanUtil.copyProperties(req, SysNotificationTemplate.class);
        templateService.updateById(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:delete")
    @SLog(title = "删除通知模板")
    @Operation(summary = "删除模板")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.removeById(id);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:notification:template:view")
    @Operation(summary = "根据编码渲染模板")
    @PostMapping("/render/{code}")
    public R<Map<String, String>> render(@PathVariable String code, @RequestBody Map<String, Object> params) {
        Map<String, String> result = templateService.renderByCode(code, params);
        return result != null ? R.success(result) : R.error("模板不存在或已禁用");
    }
}
