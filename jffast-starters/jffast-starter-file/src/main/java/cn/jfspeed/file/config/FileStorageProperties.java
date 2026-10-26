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
package cn.jfspeed.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 文件存储 Starter 配置属性
 *
 * <pre>
 * jffast:
 *   file:
 *     storage:
 *       type: local           # 存储类型：local（本地）/ minio（MinIO）
 *       max-size: 10485760    # 最大上传大小（字节），默认 10MB
 *       allowed-types:        # 允许的文件类型（为空表示允许所有）
 *         - image/*
 *         - application/pdf
 *         - application/msword
 *         - application/vnd.openxmlformats-officedocument.*
 *       local:
 *         path: upload         # 本地存储目录
 *         access-path: /upload # 访问路径
 *       minio:
 *         endpoint: http://localhost:9000
 *         access-key: minioadmin
 *         secret-key: minioadmin
 *         bucket-name: jffast  # 桶名称
 *       aliyun-oss:            # type=aliyun-oss 时生效
 *         endpoint: https://oss-cn-hangzhou.aliyuncs.com
 *         access-key-id: ...
 *         access-key-secret: ...
 *         bucket-name: jffast
 *       tencent-cos:           # type=tencent-cos 时生效
 *         region: ap-guangzhou
 *         secret-id: ...
 *         secret-key: ...
 *         bucket-name: jffast-1250000000  # 含 APPID 后缀
 * </pre>
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.file.storage")
public class FileStorageProperties {

    /** 存储类型：local（本地）/ minio（MinIO）/ aliyun-oss（阿里云 OSS）/ tencent-cos（腾讯云 COS），默认 local */
    private String type = "local";

    /** 最大上传大小（字节），默认 10MB */
    private long maxSize = 10 * 1024 * 1024;

    /** 允许的文件类型（MIME 类型），为空表示允许所有 */
    private List<String> allowedTypes = Arrays.asList(
            "image/*",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip"
    );

    /** 本地存储配置 */
    private Local local = new Local();

    /** MinIO 存储配置 */
    private Minio minio = new Minio();

    /** 阿里云 OSS 存储配置 */
    private AliyunOss aliyunOss = new AliyunOss();

    /** 腾讯云 COS 存储配置 */
    private TencentCos tencentCos = new TencentCos();

    /**
     * 本地存储配置
     */
    @Data
    public static class Local {
        /** 本地存储目录，默认 upload */
        private String path = "upload";
        /** 访问路径，默认 /upload */
        private String accessPath = "/upload";
    }

    /**
     * MinIO 存储配置
     */
    @Data
    public static class Minio {
        /** MinIO 端点地址 */
        private String endpoint;
        /** MinIO Access Key */
        private String accessKey;
        /** MinIO Secret Key */
        private String secretKey;
        /** 桶名称，默认 jffast */
        private String bucketName = "jffast";
    }

    /**
     * 阿里云 OSS 存储配置
     */
    @Data
    public static class AliyunOss {
        /** Endpoint，如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;
        /** AccessKey ID */
        private String accessKeyId;
        /** AccessKey Secret */
        private String accessKeySecret;
        /** 桶名称，默认 jffast */
        private String bucketName = "jffast";
    }

    /**
     * 腾讯云 COS 存储配置
     */
    @Data
    public static class TencentCos {
        /** 地域，如 ap-guangzhou */
        private String region;
        /** SecretId */
        private String secretId;
        /** SecretKey */
        private String secretKey;
        /** 桶名称（含 APPID 后缀），如 jffast-1250000000 */
        private String bucketName = "jffast";
    }
}
