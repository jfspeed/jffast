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
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.system.domain.dto.SysTaskQueryRequest;
import cn.jfspeed.system.entity.SysTask;
import cn.jfspeed.system.service.SysTaskRunner;
import cn.jfspeed.system.service.SysTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 异步任务（导入导出）
 *
 * @author jffast
 */
@Slf4j
@RestController
@RequestMapping("/sys/task")
@Tag(name = "异步任务")
public class SysTaskController extends BaseController<SysTaskService, SysTask> {

    @Resource
    private SysTaskRunner sysTaskRunner;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:task:view")
    @Operation(summary = "异步任务分页列表")
    public R page(SysTaskQueryRequest query) {
        return R.success(service.page(query, currentOwnerId()));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:task:view")
    @Operation(summary = "根据id获取异步任务")
    public R getById(@PathVariable("id") Long id) {
        SysTask task = getOwnedTask(id);
        if (task == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "任务不存在或无权访问");
        }
        // 不向接口调用方暴露服务器路径
        task.setFilePath(null);
        return R.success(task);
    }

    @GetMapping("/download/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:task:download")
    @Operation(summary = "下载任务结果文件")
    public Object download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        SysTask task = getOwnedTask(id);
        if (task == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "任务不存在或无权访问");
        }
        Path file = resolveTaskFile(task);
        if (file == null) {
            return R.error("文件不存在或已清理");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = StrUtil.isNotBlank(task.getFileName()) ? task.getFileName() : "任务结果.xlsx";
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("\\+", "%20"));
        try (InputStream in = Files.newInputStream(file); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
        return null;
    }

    @SLog(title = "删除异步任务")
    @DeleteMapping("/delete/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:task:delete")
    @Operation(summary = "删除异步任务")
    public R delete(@PathVariable("id") Long id) {
        SysTask task = getOwnedTask(id);
        if (task == null) {
            return R.error(AuthErrorCode.FORBIDDEN.getCode(), "任务不存在或无权访问");
        }
        service.removeById(id);
        if (StrUtil.isNotBlank(task.getFilePath())) {
            try {
                Path file = resolveTaskFile(task);
                if (file != null) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                log.warn("删除任务文件失败：{}", task.getFilePath(), e);
            }
        }
        return R.success();
    }

    /** 超管返回 null（全量），普通用户按 create_by 过滤 */
    private Long currentOwnerId() {
        return LoginUtil.isSuperAdmin() ? null : LoginUtil.getLoginUserId();
    }

    private SysTask getOwnedTask(Long id) {
        SysTask task = service.getById(id);
        if (task == null) {
            return null;
        }
        Long ownerId = currentOwnerId();
        return ownerId == null || ownerId.equals(task.getCreateBy()) ? task : null;
    }

    /** 仅允许读取任务目录内的文件，防 DB 篡改导致任意文件读取 */
    private Path resolveTaskFile(SysTask task) throws IOException {
        if (StrUtil.isBlank(task.getFilePath())) {
            return null;
        }
        Path base = sysTaskRunner.taskDir().toAbsolutePath().normalize();
        Path file = Paths.get(task.getFilePath()).toAbsolutePath().normalize();
        if (!file.startsWith(base)) {
            log.warn("任务文件路径越界，taskId={}，filePath={}", task.getId(), task.getFilePath());
            return null;
        }
        return Files.exists(file) ? file : null;
    }
}
