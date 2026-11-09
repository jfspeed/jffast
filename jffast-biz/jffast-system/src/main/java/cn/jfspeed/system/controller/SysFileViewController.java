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

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.context.TenantIds;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.FileUrlSignUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.file.service.FileUrlSigner;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.service.SysFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 文件签名直显端点（匿名，凭签名而非凭登录态）。
 *
 * <p>{@code GET /sys/file/view/{id}?ticket=...}
 *
 * <p><b>为什么需要匿名</b>：{@code <img src>} / {@code <t-avatar>} 这类场景无法携带
 * Authorization 头；此前靠「/upload/** 静态映射」直出，等于「拿到路径就能读」。
 * 现在改为：服务端签发短期票据，浏览器凭票据直显，票据本身即凭据。
 *
 * <p><b>校验链</b>（任一不通过都统一「文件不存在或已清理」，不区分原因，防枚举）：
 * <ol>
 *   <li>票据 HMAC 有效（{@link FileUrlSignUtil#verify}）；</li>
 *   <li>票据未过期；</li>
 *   <li>路径里的 id 与票据里的 id 一致；</li>
 *   <li>票据里的租户与记录 {@code tenant_id} 一致（跨租户票据不可复用）；</li>
 *   <li>记录存在。</li>
 * </ol>
 *
 * <p><b>降级态不装配</b>：{@code jffast.file.record.required=false}（显式关闭上传登记）时
 * 本控制器整体不注册——没有归属记录就没有「归属」可言，签名直显必须一并关掉。
 *
 * <p>失败口径：HTTP 200 + 业务码 400（与全局异常约定一致，{@code <img>} 表现为破图属预期）。
 *
 * @author jffast
 */
@Slf4j
@RestController
@RequestMapping("/sys/file")
@ConditionalOnProperty(prefix = "jffast.file.record", name = "required", havingValue = "true", matchIfMissing = true)
@Tag(name = "文件直显")
public class SysFileViewController {

    @Resource
    private FileStorageService fileStorageService;

    @Resource
    private SysFileService sysFileService;

    @Resource
    private FileUrlSigner fileUrlSigner;

    @GetMapping("/view/{id}")
    @Operation(summary = "按签名直显文件")
    public Object view(@PathVariable("id") Long id,
                       @RequestParam(value = "ticket", required = false) String ticket,
                       HttpServletResponse response) throws IOException {
        FileUrlSignUtil.FileUrlTicket parsed = fileUrlSigner.verify(ticket);
        if (parsed.fileId() != id) {
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        SysFile file = sysFileService.getById(id);
        if (file == null || StrUtil.isBlank(file.getUrl())) {
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        if (!TenantIds.normalize(file.getTenantId()).equals(parsed.tenantId())) {
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        response.setContentType(StrUtil.isNotBlank(file.getMime()) ? file.getMime() : "application/octet-stream");
        // 内联直显 + 禁止浏览器猜测类型 + 沙箱化：避免上传的 HTML/SVG 在本站源下执行脚本
        response.setHeader("Content-Disposition", "inline");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'");
        try (InputStream in = fileStorageService.open(file.getUrl());
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            log.warn("签名直显失败：id={}", id, e);
            throw new BusinessException(FileUrlSignUtil.INVALID_MESSAGE);
        }
        return null;
    }
}
