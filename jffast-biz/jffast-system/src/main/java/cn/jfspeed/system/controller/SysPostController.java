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
import cn.jfspeed.common.core.util.IdUtils;
import cn.jfspeed.system.domain.dto.SysPostQueryRequest;
import cn.jfspeed.system.domain.dto.SysPostRequest;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.Idempotent;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.service.SysPostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * 岗位管理
 *
 * @author jffast
 */
@RestController
@RequestMapping("/sys/post")
@Tag(name = "岗位管理")
public class SysPostController extends BaseController<SysPostService, SysPost> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:post:view")
    @Operation(summary = "岗位分页列表")
    public R page(SysPostQueryRequest query) {
        return R.success(service.page(query));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:post:view")
    @Operation(summary = "根据id获取信息")
    public R getById(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增岗位")
    @PostMapping("/create")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:post:add")
    @RateLimit(value = "default")
    @Idempotent(expire = 5)
    @Operation(summary = "新增岗位")
    public R insert(@RequestBody @Valid SysPostRequest req) {
        SysPost post = BeanUtil.copyProperties(req, SysPost.class);
        if (service.existsByCode(post.getPostCode(), post.getId())) {
            return R.error("岗位编码已存在");
        }
        post.setId(IdUtils.snowflakeShort());
        service.save(post);
        return R.success();
    }

    @SLog(title = "修改岗位")
    @PutMapping("/update")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:post:update")
    @DistributedLock(key = "'post:' + #req.id", expireTime = 10000)
    @Idempotent(expire = 5)
    @Operation(summary = "修改岗位")
    public R update(@RequestBody @Valid SysPostRequest req) {
        SysPost post = BeanUtil.copyProperties(req, SysPost.class);
        if (service.existsByCode(post.getPostCode(), post.getId())) {
            return R.error("岗位编码已存在");
        }
        service.updateById(post);
        return R.success();
    }

    @SLog(title = "删除岗位")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:post:delete")
    @Operation(summary = "删除岗位")
    public R delete(@PathVariable("id") Long id) {
        service.removeById(id);
        return R.success();
    }
}
