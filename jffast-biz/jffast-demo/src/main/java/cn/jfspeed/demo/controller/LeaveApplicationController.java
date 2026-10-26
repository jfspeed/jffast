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
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.demo.domain.dto.LeaveApplicationQueryRequest;
import cn.jfspeed.demo.domain.dto.LeaveApplicationRequest;
import cn.jfspeed.demo.entity.LeaveApplication;
import cn.jfspeed.demo.service.LeaveApplicationService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 请假申请
 *
 * @author jffast
 */
@Tag(name = "请假申请")
@RestController
@RequestMapping("/demo/leave")
public class LeaveApplicationController {

    @Resource
    private LeaveApplicationService leaveApplicationService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:leave:view")
    @Operation(summary = "分页查询请假申请")
    @GetMapping("/page")
    public R page(LeaveApplicationQueryRequest query) {
        return R.success(leaveApplicationService.page(query));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:leave:view")
    @Operation(summary = "请假申请详情")
    @GetMapping("/detail/{id}")
    public R<LeaveApplication> detail(@PathVariable Long id) {
        return R.success(leaveApplicationService.getOwnedById(id));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:leave:add")
    @SLog(title = "新增请假申请")
    @RateLimit(value = "default")
    @Operation(summary = "新增请假申请")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid LeaveApplicationRequest req) {
        LeaveApplication entity = BeanUtil.copyProperties(req, LeaveApplication.class);
        entity.setUserId(LoginUtil.getLoginUserId());
        entity.setUserName(LoginUtil.getLoginUserName());
        entity.setStatus(0);
        leaveApplicationService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:leave:edit")
    @SLog(title = "修改请假申请")
    @Operation(summary = "修改请假申请")
    @PutMapping("/update")
    public R<Void> update(@RequestBody @Valid LeaveApplicationRequest req) {
        // 归属校验在服务层：此前 update 直接 updateById，任意持有 demo:leave:edit 的角色
        // 都能按 id 改写他人的请假申请（归属 userId/userName/status 也不允许客户端改）。
        leaveApplicationService.updateOwned(req);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:leave:delete")
    @SLog(title = "删除请假申请")
    @Operation(summary = "删除请假申请")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        // 归属校验在服务层：此前直接 removeById，可删除任意人的申请。
        leaveApplicationService.deleteOwned(id);
        return R.success();
    }
}
