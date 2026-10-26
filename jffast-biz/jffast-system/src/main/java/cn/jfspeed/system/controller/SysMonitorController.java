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
import cn.jfspeed.system.domain.vo.MonitorInfo;
import cn.jfspeed.system.service.SysMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统监控
 *
 * @author jffast
 */
@Tag(name = "系统监控")
@RestController
@RequestMapping("/sys/monitor")
public class SysMonitorController {

    @Resource
    private SysMonitorService sysMonitorService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:monitor:view")
    @Operation(summary = "获取系统监控信息")
    @GetMapping("/info")
    public R<MonitorInfo> getInfo() {
        return R.success(sysMonitorService.getMonitorInfo());
    }
}
