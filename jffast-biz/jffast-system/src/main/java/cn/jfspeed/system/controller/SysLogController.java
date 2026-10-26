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
import cn.jfspeed.system.domain.dto.SysLogQueryRequest;
import cn.jfspeed.excel.util.ExcelUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.system.domain.dto.SysLogExcel;
import cn.jfspeed.system.entity.SysLog;
import cn.jfspeed.system.service.SysLogService;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/log")
@Tag(name = "日志管理")
public class SysLogController extends BaseController<SysLogService, SysLog> {

    /** 单次导出最大行数（超出部分截断，防止一次导出拖垮内存） */
    private static final int EXPORT_MAX_ROWS = 10000;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "日志列表")
    public R list(SysLogQueryRequest sysLog, PageReq pageQuery) {
        return R.success(service.page(sysLog,pageQuery));
    }

    @GetMapping("/export")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "导出日志Excel（操作/登录日志，按查询条件过滤）")
    public void export(HttpServletResponse response, SysLogQueryRequest sysLog) {
        List<SysLogExcel> list = service.listForExport(sysLog, EXPORT_MAX_ROWS);
        ExcelUtil.export(response, "系统日志.xlsx", list, SysLogExcel.class);
    }

    @GetMapping("/titleList")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:view")
    @Operation(summary = "日志标题列表")
    public R titleList() {
        List<String> titles = service.titleList();
        return R.success(titles);
    }

    @SLog(title = "清理历史日志")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:log:delete")
    @Operation(summary = "清理指定天数前的日志")
    @DeleteMapping("/clean")
    public R<Integer> clean(@RequestParam(defaultValue = "90") int days) {
        if (days < 1) {
            return R.error("清理天数必须大于 0");
        }
        int count = service.cleanLogs(days);
        return R.success(count);
    }
}
