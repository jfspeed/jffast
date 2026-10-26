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
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * @author jffast
 */
@Slf4j
public class MinIOFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final FileStorageProperties.Minio minioProperties;
    private final FileUploadValidator validator;

    private static final String SEPARATOR = "/";

    public MinIOFileStorageService(MinioClient minioClient, FileStorageProperties fileStorageProperties) {
        this.minioClient = minioClient;
        this.minioProperties = fileStorageProperties.getMinio();
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
        return putObject(prefix, filename, validator.resolveContentType(file), file);
    }

    @Override
    public String uploadFile(String prefix, String filename, MultipartFile file) {
        validator.validate(file);
        return putObject(prefix, filename, validator.resolveContentType(file), file);
    }

    private String putObject(String prefix, String filename, String contentType, MultipartFile file) {
        String filePath = builderFilePath(prefix, filename);
        try (InputStream in = file.getInputStream()) {
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object(filePath)
                    .contentType(contentType)
                    .bucket(minioProperties.getBucketName())
                    .stream(in, file.getSize(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);
            return buildUrlPath(filePath);
        } catch (Exception ex) {
            log.error("上传文件失败", ex);
            throw new BusinessException("上传文件失败");
        }
    }

    /** 与其它存储实现一致：返回 /{bucket}/{objectKey} */
    private String buildUrlPath(String filePath) {
        return SEPARATOR + minioProperties.getBucketName() + SEPARATOR + filePath;
    }

    @Override
    public String uploadHtmlFile(String prefix, String filename, InputStream inputStream) {
        String filePath = builderFilePath(prefix, filename);
        try {
            byte[] bytes = inputStream.readAllBytes();
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object(filePath)
                    .contentType("text/html")
                    .bucket(minioProperties.getBucketName())
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build();
            minioClient.putObject(putObjectArgs);
            return buildUrlPath(filePath);
        } catch (Exception ex) {
            log.error("上传文件失败", ex);
            throw new BusinessException("上传文件失败");
        }
    }

    @Override
    public void delete(String pathUrl) {
        ObjectKey objectKey = parseObjectKey(pathUrl);
        RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder()
                .bucket(objectKey.bucket()).object(objectKey.key()).build();
        try {
            minioClient.removeObject(removeObjectArgs);
        } catch (Exception e) {
            log.error("删除文件失败", e);
            throw new BusinessException("删除文件失败");
        }
    }

    @Override
    public byte[] downloadFile(String pathUrl) {
        ObjectKey objectKey = parseObjectKey(pathUrl);
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder().bucket(objectKey.bucket()).object(objectKey.key()).build());
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

    /**
     * 解析 /{bucket}/{key}：兼容带 endpoint 前缀、带 query/fragment 以及缺少前导斜杠的历史值。
     */
    private ObjectKey parseObjectKey(String pathUrl) {
        return parseObjectKey(pathUrl, minioProperties.getEndpoint());
    }

    static ObjectKey parseObjectKey(String pathUrl, String endpoint) {
        if (StrUtil.isBlank(pathUrl)) {
            throw new BusinessException("无效的文件路径：空值");
        }
        String path = pathUrl.trim();
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        if (StrUtil.isNotEmpty(endpoint)) {
            path = path.replace(StrUtil.removeSuffix(endpoint, SEPARATOR) + SEPARATOR, "");
        }
        path = StrUtil.removePrefix(path, SEPARATOR);
        int index = path.indexOf(SEPARATOR);
        if (index <= 0 || index == path.length() - 1) {
            throw new BusinessException("无效的文件路径：" + pathUrl);
        }
        String bucket = path.substring(0, index);
        String key = path.substring(index + 1);
        if (key.contains("..")) {
            throw new BusinessException("无效的文件路径：" + pathUrl);
        }
        return new ObjectKey(bucket, key);
    }

    record ObjectKey(String bucket, String key) {
    }
}
