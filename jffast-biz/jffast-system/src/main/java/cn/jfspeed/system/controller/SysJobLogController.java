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
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.system.domain.dto.SysJobLogQueryRequest;
import cn.jfspeed.system.entity.SysJobLog;
import cn.jfspeed.system.service.SysJobLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/job/log")
@Tag(name = "任务执行日志")
public class SysJobLogController extends BaseController<SysJobLogService, SysJobLog> {

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:view")
    @Operation(summary = "任务执行日志列表")
    public R page(SysJobLogQueryRequest query, PageReq pageReq) {
        return R.success(service.page(query, pageReq));
    }

    @SLog(title = "清空任务执行日志")
    @DeleteMapping("/clean")
    // 用自己的权限位，而不是 system:job:delete：种子里的「日志清空」按钮授的是
    // system:job:log:clean，之前端点校验的却是 system:job:delete —— 授了按钮权限的人
    // 点了仍 403，反过来授了「任务删除」的人却能清空全部任务日志，越出了种子声明的授权语义。
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:job:log:clean")
    @Operation(summary = "清空任务执行日志")
    public R clean() {
        service.clean();
        return R.success();
    }
}
