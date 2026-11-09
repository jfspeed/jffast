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
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.system.domain.vo.SysFileVO;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.FileUrlSignService;
import cn.jfspeed.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    @Resource
    private FileUrlSignService fileUrlSignService;

    /** 下载上限（字节），默认 50MB；0 表示不限制 */
    @Value("${jffast.file.download-max-bytes:52428800}")
    private long downloadMaxBytes;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:view")
    @Operation(summary = "文件分页列表")
    public R<PageRes<SysFileVO>> page(SysFileQueryRequest query) {
        PageRes<SysFile> pageRes = service.page(query, service.currentOwnerId());
        List<SysFile> files = pageRes.getList() == null ? List.of() : pageRes.getList();
        // 批量换发签名 URL：一次查库，避免逐条查询的 N+1
        Map<Long, String> signedUrls = fileUrlSignService.signAttachments(
                files.stream().map(SysFile::getId).toList());
        List<SysFileVO> voList = files.stream().map(file -> {
            SysFileVO vo = new SysFileVO();
            vo.setId(file.getId());
            vo.setFileName(file.getFileName());
            vo.setSize(file.getSize());
            vo.setMime(file.getMime());
            vo.setBizType(file.getBizType());
            vo.setStorageType(file.getStorageType());
            vo.setCreateBy(file.getCreateBy());
            vo.setTenantId(file.getTenantId());
            vo.setCreateTime(file.getCreateTime());
            // 只出签名 URL，绝不出裸存储定位键
            vo.setUrl(signedUrls.get(file.getId()));
            return vo;
        }).toList();
        return R.success(new PageRes<>(pageRes.getTotal(), pageRes.getPages(), voList));
    }

    /**
     * 换发签名 URL（管理页预览 / 前端长停留自愈）。
     *
     * <p>必须先过归属校验（文件属于当前用户）才签发——否则「知道 ID」就等于「能读文件」。
     */
    @GetMapping("/signed-url/{id}")
    @Operation(summary = "换发文件签名 URL")
    public R<SignedUrlVO> signedUrl(@PathVariable("id") Long id) {
        String url = fileUrlSignService.signOwned(id);
        return R.success(new SignedUrlVO(url, fileUrlSignService.ttlSeconds()));
    }

    @GetMapping("/download/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:download")
    @Operation(summary = "下载文件")
    public Object download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        SysFile file = service.getOwnedFile(id, service.currentOwnerId());
        if (file == null || StrUtil.isBlank(file.getUrl())) {
            // 抛业务异常（HTTP 200 + 业务码 400），由前端 blob 分支统一解析并弹出 message。
            //
            // ⚠️ 不要"改成" HTTP 404/500：全局异常处理对 BusinessException 一律返回
            // HTTP 200 + body.code（这是全项目约定，见 开发规范.md §11 与
            // SysUserControllerAuthorizationCodeTest），而前端 shared/src/utils/request.ts
            // 的 blob 分支正是靠「HTTP 200 + JSON 业务体」识别下载失败并弹错；
            // 一旦改成 404，前端会走"404 不弹全局提示"分支 ⇒ 下载失败变成**零提示**。
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        if (downloadMaxBytes > 0 && file.getSize() != null && file.getSize() > downloadMaxBytes) {
            throw new BusinessException("文件超过下载上限");
        }
        response.setContentType(StrUtil.isNotBlank(file.getMime()) ? file.getMime() : "application/octet-stream");
        response.setCharacterEncoding("utf-8");
        String fileName = StrUtil.isNotBlank(file.getFileName()) ? file.getFileName() : "file";
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("\\+", "%20"));
        // 流式输出：不再整文件读进 byte[]（大文件会把堆撑爆）
        try (InputStream in = fileStorageService.open(file.getUrl());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            log.warn("下载文件失败：{}", file.getUrl(), e);
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        return null;
    }

    @SLog(title = "删除文件")
    @DeleteMapping("/delete/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:file:delete")
    @Operation(summary = "删除文件（记录 + 存储文件）")
    public R delete(@PathVariable("id") Long id) {
        SysFile file = service.getOwnedFile(id, service.currentOwnerId());
        if (file == null) {
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
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
                SysFile file = service.getOwnedFile(id, service.currentOwnerId());
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

    /**
     * 签名 URL 出参
     */
    public static class SignedUrlVO {

        /** 签名 URL（API 根相对路径） */
        private final String url;

        /** 有效期（秒） */
        private final long expiresIn;

        public SignedUrlVO(String url, long expiresIn) {
            this.url = url;
            this.expiresIn = expiresIn;
        }

        public String getUrl() {
            return url;
        }

        public long getExpiresIn() {
            return expiresIn;
        }
    }
}
