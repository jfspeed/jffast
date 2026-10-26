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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import cn.jfspeed.file.record.SysFileRecorder;
import cn.jfspeed.file.service.AliyunOssFileStorageService;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.file.service.LocalFileStorageService;
import cn.jfspeed.file.service.MinIOFileStorageService;
import cn.jfspeed.file.service.RecordingFileStorageService;
import cn.jfspeed.file.service.TencentCosFileStorageService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 文件存储自动配置：local（默认）/ MinIO / 阿里云 OSS / 腾讯云 COS。
 *
 * <p>多云 SDK（aliyun-sdk-oss、cos_api）为 optional 依赖，使用方按需引入。为避免 SDK 缺席时
 * Spring 内省本类方法签名触发 {@link NoClassDefFoundError}，各云厂商的客户端与服务 bean
 * 隔离到独立嵌套配置类，并以 {@code @ConditionalOnClass(name = "...")}（字符串形式，绝不触发类加载）
 * 做门：SDK 缺席时对应嵌套配置整体跳过，其方法永不被内省，应用正常以 local/minio 启动。
 *
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(FileStorageService.class)
@EnableConfigurationProperties(FileStorageProperties.class)
public class JfFastFileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorageService(FileStorageProperties properties,
                                                      ObjectProvider<SysFileRecorder> recorder) {
        return new RecordingFileStorageService(new LocalFileStorageService(properties), recorder.getIfAvailable());
    }

    /**
     * MinIO：minio SDK 为硬依赖（随包），始终在 classpath。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MinioClient.class)
    static class MinioConfig {

        @Bean
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "minio", matchIfMissing = false)
        public MinioClient minioClient(FileStorageProperties properties) {
            FileStorageProperties.Minio minio = properties.getMinio();
            return MinioClient.builder()
                    .credentials(minio.getAccessKey(), minio.getSecretKey())
                    .endpoint(minio.getEndpoint())
                    .build();
        }

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "minio", matchIfMissing = false)
        public FileStorageService minioFileStorageService(MinioClient minioClient, FileStorageProperties properties,
                                                          ObjectProvider<SysFileRecorder> recorder) {
            return new RecordingFileStorageService(new MinIOFileStorageService(minioClient, properties), recorder.getIfAvailable());
        }
    }

    /**
     * 阿里云 OSS：aliyun-sdk-oss 为 optional 依赖，缺席时本配置整体跳过。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.aliyun.oss.OSS")
    static class AliyunOssConfig {

        @Bean
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "aliyun-oss", matchIfMissing = false)
        public OSS aliyunOssClient(FileStorageProperties properties) {
            FileStorageProperties.AliyunOss aliyunOss = properties.getAliyunOss();
            return new OSSClientBuilder().build(
                    aliyunOss.getEndpoint(), aliyunOss.getAccessKeyId(), aliyunOss.getAccessKeySecret());
        }

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "aliyun-oss", matchIfMissing = false)
        public FileStorageService aliyunOssFileStorageService(OSS ossClient, FileStorageProperties properties,
                                                              ObjectProvider<SysFileRecorder> recorder) {
            return new RecordingFileStorageService(new AliyunOssFileStorageService(ossClient, properties), recorder.getIfAvailable());
        }
    }

    /**
     * 腾讯云 COS：cos_api 为 optional 依赖，缺席时本配置整体跳过。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.qcloud.cos.COSClient")
    static class TencentCosConfig {

        @Bean
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "tencent-cos", matchIfMissing = false)
        public COSClient tencentCosClient(FileStorageProperties properties) {
            FileStorageProperties.TencentCos tencentCos = properties.getTencentCos();
            COSCredentials credentials = new BasicCOSCredentials(tencentCos.getSecretId(), tencentCos.getSecretKey());
            ClientConfig clientConfig = new ClientConfig(new Region(tencentCos.getRegion()));
            return new COSClient(credentials, clientConfig);
        }

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "tencent-cos", matchIfMissing = false)
        public FileStorageService tencentCosFileStorageService(COSClient cosClient, FileStorageProperties properties,
                                                               ObjectProvider<SysFileRecorder> recorder) {
            return new RecordingFileStorageService(new TencentCosFileStorageService(cosClient, properties), recorder.getIfAvailable());
        }
    }

    /**
     * 上传登记（sys_file）：spring-jdbc 为 optional 依赖，缺席时本配置整体跳过，
     * 存储服务退化为不登记（与多云 SDK 同一隔离手法：字符串条件 + 嵌套配置，
     * 避免方法签名内省触发 {@link NoClassDefFoundError}）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    static class FileRecordConfig {

        @Bean
        public SysFileRecorder sysFileRecorder(ObjectProvider<JdbcTemplate> jdbc, FileStorageProperties properties) {
            return new SysFileRecorder(jdbc.getIfAvailable(), properties);
        }
    }

}
