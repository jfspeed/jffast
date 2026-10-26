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
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.service.SysOnlineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/online")
@Tag(name = "在线用户")
public class SysOnlineController {

    @Resource
    private SysOnlineService service;

    @GetMapping("/list")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:online:view")
    @RateLimit(value = "default")
    @Operation(summary = "在线用户列表")
    public R list() {
        return R.success(service.onlineList());
    }

    @SLog(title = "强制下线用户")
    @DeleteMapping("/kick/{loginId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:online:kick")
    @Operation(summary = "强制下线")
    public R kick(@PathVariable("loginId") Long loginId) {
        Long current = LoginUtil.getLoginUserId();
        if (Objects.equals(current, loginId)) {
            return R.error("不能强制下线当前登录账号");
        }
        service.kick(loginId);
        return R.success();
    }

    @SLog(title = "批量强制下线用户")
    @DeleteMapping("/kickBatch")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:online:kick")
    @Operation(summary = "批量强制下线")
    public R kickBatch(@RequestBody List<Long> loginIds) {
        Long current = LoginUtil.getLoginUserId();
        if (loginIds.contains(current)) {
            return R.error("不能强制下线当前登录账号");
        }
        service.kickBatch(loginIds);
        return R.success();
    }
}
