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
package cn.jfspeed.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.flow.domain.dto.WfCategoryRequest;
import jakarta.validation.Valid;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.flow.entity.WfCategory;
import cn.jfspeed.flow.service.WfCategoryService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 流程分类管理 Controller。
 *
 * @author jffast
 */
@RestController
@RequestMapping("/flow/category")
@Tag(name = "流程分类管理")
public class WfCategoryController extends BaseController<WfCategoryService, WfCategory> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:view")
    @Operation(summary = "分页查询流程分类")
    public R page(@RequestParam(required = false) String categoryName,
                  @RequestParam(required = false) String categoryCode,
                  PageReq pageReq) {
        return R.success(service.page(categoryName, categoryCode, pageReq));
    }

    @GetMapping("/list")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:view")
    @Operation(summary = "查询全部启用分类（下拉用）")
    public R list() {
        List<WfCategory> list = service.listEnabled();
        return R.success(list);
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:view")
    @Operation(summary = "分类详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增流程分类")
    @PostMapping("/create")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:add")
    @Operation(summary = "新增流程分类")
    public R create(@RequestBody @Valid WfCategoryRequest req) {
        WfCategory entity = BeanUtil.copyProperties(req, WfCategory.class);
        service.save(entity);
        return R.success();
    }

    @SLog(title = "修改流程分类")
    @PutMapping("/update")
    @DistributedLock(key = "'flow:category:' + #req.id", expireTime = 10000)
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:update")
    @Operation(summary = "修改流程分类")
    public R update(@RequestBody @Valid WfCategoryRequest req) {
        WfCategory entity = BeanUtil.copyProperties(req, WfCategory.class);
        service.updateById(entity);
        return R.success();
    }

    @SLog(title = "删除流程分类")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:category:delete")
    @Operation(summary = "删除流程分类")
    public R delete(@PathVariable("id") Long id) {
        service.removeById(id);
        return R.success();
    }
}
