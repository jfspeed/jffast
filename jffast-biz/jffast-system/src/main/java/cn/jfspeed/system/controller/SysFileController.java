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
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件管理（记录由 jffast-starter-file 上传时自动登记）
 *
 * @author jffast
 */
@Slf4j
@RestController
@RequestMapping("/sys/file")
@Tag(name = "文件管理")
public class SysFileController extends BaseController<SysFileService, SysFile> {

    @Resource
    private FileStorageService fileStorageService;

    /** 下载上限（字节），0 表示不限制；大文件下载仍是 byte[] 全量，见 R-09 长期方案 */
    @Value("${jffast.file.download-max-bytes:0}")
    private long downloadMaxBytes;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:view")
    @Operation(summary = "文件分页列表")
    public R page(SysFileQueryRequest query) {
        return R.success(service.page(query, currentOwnerId()));
    }

    /**
     * 当前请求可见的文件归属：超管返回 null（全量），普通用户返回自己的用户 ID。
     *
     * <p>⚠️ <b>取不到登录用户时必须拒绝，不能退化成 null</b>：本方法下游把 null 当作
     * 「不限制归属」，于是 {@code getLoginUserId()} 返回 null（会话失效、Session 里没有 user、
     * 读取会话抛异常被吞）时，一次会话读取失败就等于给全量文件开了后门——与本轮要堵的
     * 越权洞同型，只是换了个触发路径。因此「超管」与「身份不可解析」必须分成两条路：
     * 只有超管才允许 null。
     */
    private Long currentOwnerId() {
        if (LoginUtil.isSuperAdmin()) {
            return null;
        }
        Long userId = LoginUtil.getLoginUserId();
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.NOT_LOGIN.getCode(), "登录状态已失效，请重新登录");
        }
        return userId;
    }

    /**
     * 按 id 取记录并校验归属。
     *
     * <p>越权与「记录不存在」必须是同一句提示、同一个分支：否则可以用返回值差异枚举出
     * 哪些 id 属于别人（{@code /sys/task/download} 用的是同一口径）。
     */
    private SysFile getOwnedFile(Long id) {
        SysFile file = service.getById(id);
        if (file == null) {
            return null;
        }
        Long ownerId = currentOwnerId();
        return ownerId == null || ownerId.equals(file.getCreateBy()) ? file : null;
    }

    @GetMapping("/download/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:download")
    @Operation(summary = "下载文件")
    public Object download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        SysFile file = getOwnedFile(id);
        if (file == null || StrUtil.isBlank(file.getUrl())) {
            // 抛业务异常（HTTP 200 + 业务码 400），由前端 blob 分支统一解析并弹出 message。
            //
            // ⚠️ 不要"改成" HTTP 404/500：全局异常处理对 BusinessException 一律返回
            // HTTP 200 + body.code（这是全项目约定，见 开发规范.md §11 与
            // SysUserControllerAuthorizationCodeTest），而前端 shared/src/utils/request.ts
            // 的 blob 分支正是靠「HTTP 200 + JSON 业务体」识别下载失败并弹错；
            // 一旦改成 404，前端会走"404 不弹全局提示"分支 ⇒ 下载失败变成**零提示**。
            throw new BusinessException("文件不存在或已清理");
        }
        if (downloadMaxBytes > 0 && file.getSize() != null && file.getSize() > downloadMaxBytes) {
            throw new BusinessException("文件超过下载上限");
        }
        byte[] content;
        try {
            content = fileStorageService.downloadFile(file.getUrl());
        } catch (Exception e) {
            log.warn("下载文件失败：{}", file.getUrl(), e);
            throw new BusinessException("文件不存在或已清理");
        }
        response.setContentType(StrUtil.isNotBlank(file.getMime()) ? file.getMime() : "application/octet-stream");
        response.setCharacterEncoding("utf-8");
        String fileName = StrUtil.isNotBlank(file.getFileName()) ? file.getFileName() : "file";
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("\\+", "%20"));
        try (OutputStream out = response.getOutputStream()) {
            out.write(content);
        }
        return null;
    }

    @SLog(title = "删除文件")
    @DeleteMapping("/delete/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:delete")
    @Operation(summary = "删除文件（记录 + 存储文件）")
    public R delete(@PathVariable("id") Long id) {
        SysFile file = getOwnedFile(id);
        if (file == null) {
            throw new BusinessException("文件不存在或已清理");
        }
        doDelete(file);
        return R.success();
    }

    @SLog(title = "批量删除文件")
    @DeleteMapping("/batch")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:delete")
    @RateLimit(value = "default")
    @Operation(summary = "批量删除文件（记录 + 存储文件）")
    public R deleteBatch(@RequestBody List<Long> ids) {
        if (ids != null) {
            for (Long id : ids) {
                // 不属于本人的 id 静默跳过：批量接口逐个报错会把「存在但无权限」暴露成可枚举信号
                SysFile file = getOwnedFile(id);
                if (file != null) {
                    doDelete(file);
                }
            }
        }
        return R.success();
    }

    private void doDelete(SysFile file) {
        // 先删存储再删记录：顺序反过来时存储删除失败会留下永远无人引用的孤儿文件
        try {
            fileStorageService.delete(file.getUrl());
        } catch (Exception e) {
            log.warn("删除存储文件失败：{}", file.getUrl(), e);
            throw new BusinessException("删除存储文件失败，记录未删除，请稍后重试");
        }
        service.removeById(file.getId());
    }
}
