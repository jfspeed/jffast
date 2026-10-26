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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

import org.springframework.web.multipart.MultipartFile;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.ObjectMetadata;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯云 COS 文件存储（jffast.file.storage.type=tencent-cos）
 *
 * @author jffast
 */
@Slf4j
public class TencentCosFileStorageService implements FileStorageService {

    private static final String SEPARATOR = "/";

    private final COSClient cosClient;
    private final FileStorageProperties.TencentCos cosProperties;
    private final FileUploadValidator validator;

    public TencentCosFileStorageService(COSClient cosClient, FileStorageProperties fileStorageProperties) {
        this.cosClient = cosClient;
        this.cosProperties = fileStorageProperties.getTencentCos();
        this.validator = new FileUploadValidator(fileStorageProperties);
    }

    public String builderFilePath(String dirPath, String filename) {
        StringBuilder stringBuilder = new StringBuilder(50);
        if (!StrUtil.isEmpty(dirPath)) {
            stringBuilder.append(dirPath).append(SEPARATOR);
        }
        String todayStr = LocalDateTimeUtil.format(LocalDateTime.now(), "yyyyMMdd-HHmmss");
        stringBuilder.append(todayStr).append(SEPARATOR);
        stringBuilder.append(filename);
        return stringBuilder.toString();
    }

    @Override
    public String uploadImgFile(String prefix, String filename, MultipartFile file) {
        validator.validateImage(file);
        return upload(prefix, filename, validator.resolveContentType(file), file);
    }

    @Override
    public String uploadFile(String prefix, String filename, MultipartFile file) {
        validator.validate(file);
        return upload(prefix, filename, validator.resolveContentType(file), file);
    }

    @Override
    public String uploadHtmlFile(String prefix, String filename, InputStream inputStream) {
        String filePath = builderFilePath(prefix, filename);
        try {
            byte[] bytes = inputStream.readAllBytes();
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("text/html");
            metadata.setContentLength(bytes.length);
            cosClient.putObject(cosProperties.getBucketName(), filePath, new ByteArrayInputStream(bytes), metadata);
            return buildUrlPath(filePath);
        } catch (Exception ex) {
            log.error("上传文件失败", ex);
            throw new BusinessException("上传文件失败");
        }
    }

    private String upload(String prefix, String filename, String contentType, MultipartFile file) {
        String filePath = builderFilePath(prefix, filename);
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength(file.getSize());
            cosClient.putObject(cosProperties.getBucketName(), filePath, in, metadata);
            return buildUrlPath(filePath);
        } catch (Exception ex) {
            log.error("上传文件失败", ex);
            throw new BusinessException("上传文件失败");
        }
    }

    /** 与 MinIO 实现一致：返回 /{bucket}/{objectKey} */
    private String buildUrlPath(String filePath) {
        return SEPARATOR + cosProperties.getBucketName() + SEPARATOR + filePath;
    }

    @Override
    public void delete(String pathUrl) {
        ObjectKey objectKey = parseObjectKey(pathUrl);
        try {
            cosClient.deleteObject(objectKey.bucket, objectKey.key);
        } catch (Exception e) {
            log.error("删除文件失败", e);
            throw new BusinessException("删除文件失败");
        }
    }

    @Override
    public byte[] downloadFile(String pathUrl) {
        ObjectKey objectKey = parseObjectKey(pathUrl);
        try (COSObject cosObject = cosClient.getObject(objectKey.bucket, objectKey.key);
             InputStream inputStream = cosObject.getObjectContent();
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buff = new byte[4096];
            int rc;
            while ((rc = inputStream.read(buff)) > 0) {
                byteArrayOutputStream.write(buff, 0, rc);
            }
            return byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            log.error("下载文件失败", e);
            throw new BusinessException("下载文件失败");
        }
    }

    /** 解析 /{bucket}/{key}（允许前导斜杠） */
    private ObjectKey parseObjectKey(String pathUrl) {
        String key = StrUtil.removePrefix(pathUrl, SEPARATOR);
        int index = key.indexOf(SEPARATOR);
        if (index <= 0 || index == key.length() - 1) {
            throw new BusinessException("无效的文件路径：" + pathUrl);
        }
        return new ObjectKey(key.substring(0, index), key.substring(index + 1));
    }

    private record ObjectKey(String bucket, String key) {
    }
}
