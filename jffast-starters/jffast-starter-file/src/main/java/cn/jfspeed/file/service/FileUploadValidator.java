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
package cn.jfspeed.file.service;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

/**
 * 用户上传文件的统一校验：大小、危险扩展名、MIME 白名单。
 * <p>
 * 各存储后端共用同一套规则，避免「本地存储有校验、远程存储没有」的防护落差。
 *
 * @author jffast
 */
final class FileUploadValidator {

    /**
     * 禁止用户上传的主动内容扩展名：这些文件一旦被同源内联访问，浏览器会执行其中的脚本，
     * 构成存储型 XSS。仅作用于用户上传；服务端生成的报告/模板走 uploadHtmlFile，不受此限制。
     */
    private static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "html", "htm", "xhtml", "shtml", "svg", "xml", "js", "mjs", "jsx", "swf", "htc", "xsl", "xslt");

    private final FileStorageProperties properties;

    FileUploadValidator(FileStorageProperties properties) {
        this.properties = properties;
    }

    void validate(MultipartFile file) {
        if (file == null) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxSize()) {
            throw new BusinessException("文件大小超过限制，最大允许 " + (properties.getMaxSize() / 1024 / 1024) + "MB");
        }
        String originalName = file.getOriginalFilename();
        if (StrUtil.isNotBlank(originalName)) {
            String normalized = originalName.replace("\\", "/");
            int dot = normalized.lastIndexOf('.');
            if (dot >= 0 && dot < normalized.length() - 1) {
                String ext = normalized.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (DANGEROUS_EXTENSIONS.contains(ext)) {
                    throw new BusinessException("不允许上传该类型文件: " + ext);
                }
            }
        }
        if (properties.getAllowedTypes() != null && !properties.getAllowedTypes().isEmpty()) {
            String contentType = file.getContentType();
            if (contentType == null || !isAllowedType(contentType)) {
                throw new BusinessException("文件类型不允许: " + contentType);
            }
        }
    }

    /**
     * 图片上传：在通用校验之上要求实际 MIME 为 image/*，避免把任意文件塞进图片字段。
     */
    void validateImage(MultipartFile file) {
        validate(file);
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException("仅允许上传图片文件");
        }
    }

    /**
     * 返回可用于存储的 MIME：优先取客户端声明值，缺失时用二进制流兜底。
     */
    String resolveContentType(MultipartFile file) {
        return StrUtil.isBlank(file.getContentType()) ? "application/octet-stream" : file.getContentType();
    }

    private boolean isAllowedType(String contentType) {
        for (String allowedType : properties.getAllowedTypes()) {
            if (allowedType.endsWith("/*")) {
                String prefix = allowedType.substring(0, allowedType.length() - 2);
                if (contentType.startsWith(prefix + "/")) {
                    return true;
                }
            } else if (allowedType.contains("*")) {
                String prefix = allowedType.substring(0, allowedType.indexOf("*"));
                if (contentType.startsWith(prefix)) {
                    return true;
                }
            } else if (allowedType.equals(contentType)) {
                return true;
            }
        }
        return false;
    }
}
