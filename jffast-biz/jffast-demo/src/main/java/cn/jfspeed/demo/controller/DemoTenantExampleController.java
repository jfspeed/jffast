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
package cn.jfspeed.demo.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.demo.domain.dto.DemoTenantExampleRequest;
import jakarta.validation.Valid;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.demo.entity.DemoTenantExample;
import cn.jfspeed.demo.service.DemoTenantExampleService;
import cn.jfspeed.log.annotation.SLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 租户隔离示例。
 * <p>
 * Base 版租户拦截仅预留：开关关闭时本接口表现为普通 CRUD；
 * 开启租户拦截后自动按当前租户隔离，业务代码无感知。
 *
 * @author jffast
 */
@RestController
@RequestMapping("/demo/tenant-example")
@Tag(name = "租户隔离示例")
public class DemoTenantExampleController extends BaseController<DemoTenantExampleService, DemoTenantExample> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:tenant-example:view")
    @Operation(summary = "分页查询")
    public R page(@RequestParam(defaultValue = "1") Integer pageNo,
                  @RequestParam(defaultValue = "10") Integer pageSize,
                  @RequestParam(required = false) String title,
                  @RequestParam(required = false) Integer status) {
        return R.success(service.page(pageNo, pageSize, title, status));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:tenant-example:view")
    @Operation(summary = "详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增租户示例")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:tenant-example:add")
    @Operation(summary = "新增")
    public R create(@RequestBody @Valid DemoTenantExampleRequest req) {
        DemoTenantExample entity = BeanUtil.copyProperties(req, DemoTenantExample.class);
        service.save(entity);
        return R.success();
    }

    @SLog(title = "修改租户示例")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:tenant-example:edit")
    @Operation(summary = "修改")
    public R update(@RequestBody @Valid DemoTenantExampleRequest req) {
        DemoTenantExample entity = BeanUtil.copyProperties(req, DemoTenantExample.class);
        service.updateById(entity);
        return R.success();
    }

    @SLog(title = "删除租户示例")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:tenant-example:delete")
    @Operation(summary = "删除")
    public R delete(@PathVariable("id") Long id) {
        service.removeById(id);
        return R.success();
    }
}
