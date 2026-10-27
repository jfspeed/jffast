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

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * @author jffast
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties properties;
    private final FileUploadValidator validator;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
        this.validator = new FileUploadValidator(properties);
    }

    private static final String SEPARATOR = "/";

    @Override
    public String uploadFile(String prefix, String filename, MultipartFile file) {
        validator.validate(file);
        return doUpload(prefix, filename, file);
    }

    @Override
    public String uploadImgFile(String prefix, String filename, MultipartFile file) {
        validator.validateImage(file);
        return doUpload(prefix, filename, file);
    }

    @Override
    public String uploadHtmlFile(String prefix, String filename, InputStream inputStream) {
        String filePath = builderFilePath(prefix, filename);
        try {
            Path targetPath = resolveSafe(filePath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath);
            return properties.getLocal().getAccessPath() + SEPARATOR + filePath;
        } catch (IOException e) {
            log.error("上传文件失败", e);
            throw new BusinessException("上传文件失败");
        }
    }

    @Override
    public void delete(String pathUrl) {
        String relativePath = extractRelativePath(pathUrl);
        try {
            Path targetPath = resolveSafe(relativePath);
            Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            log.error("删除文件失败", e);
            throw new BusinessException("删除文件失败");
        }
    }

    @Override
    public byte[] downloadFile(String pathUrl) {
        String relativePath = extractRelativePath(pathUrl);
        try {
            Path targetPath = resolveSafe(relativePath);
            return Files.readAllBytes(targetPath);
        } catch (IOException e) {
            log.error("下载文件失败", e);
            throw new BusinessException("下载文件失败");
        }
    }

    private String doUpload(String prefix, String filename, MultipartFile file) {
        String filePath = builderFilePath(prefix, filename);
        try {
            Path targetPath = resolveSafe(filePath);
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
            return properties.getLocal().getAccessPath() + SEPARATOR + filePath;
        } catch (IOException e) {
            log.error("上传文件失败", e);
            throw new BusinessException("上传文件失败");
        }
    }

    private String builderFilePath(String dirPath, String filename) {
        StringBuilder sb = new StringBuilder(50);
        if (StrUtil.isNotEmpty(dirPath)) {
            sb.append(dirPath).append(SEPARATOR);
        }
        String todayStr = LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd-HHmmss");
        // 秒级时间戳不足以防重名：同一秒内上传同名文件会直接覆盖。
        sb.append(todayStr).append('-').append(IdUtil.fastSimpleUUID(), 0, 8).append(SEPARATOR);
        sb.append(sanitizeFilename(filename));
        return sb.toString();
    }

    private String extractRelativePath(String pathUrl) {
        String relativePath = pathUrl.replace(properties.getLocal().getAccessPath(), "");
        if (relativePath.startsWith(SEPARATOR)) {
            relativePath = relativePath.substring(1);
        }
        return relativePath;
    }

    private String sanitizeFilename(String filename) {
        if (StrUtil.isBlank(filename)) {
            throw new BusinessException("文件名不能为空");
        }
        String normalized = filename.replace("\\", "/");
        if (normalized.contains("..") || normalized.contains("/")) {
            throw new BusinessException("文件名非法: " + filename);
        }
        return normalized.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private Path resolveSafe(String relativePath) {
        Path base = Paths.get(properties.getLocal().getPath()).toAbsolutePath().normalize();
        Path target = base.resolve(relativePath).normalize();
        if (!target.startsWith(base)) {
            throw new BusinessException("非法文件路径: " + relativePath);
        }
        return target;
    }
}
