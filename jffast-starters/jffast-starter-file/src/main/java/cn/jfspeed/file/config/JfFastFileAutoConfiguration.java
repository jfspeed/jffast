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

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.file.record.SysFileReader;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * 文件存储自动配置：local（默认）/ MinIO / 阿里云 OSS / 腾讯云 COS。
 *
 * <p>多云 SDK（aliyun-sdk-oss、cos_api）为 optional 依赖，使用方按需引入。为避免 SDK 缺席时
 * Spring 内省本类方法签名触发 {@link NoClassDefFoundError}，各云厂商的客户端与服务 bean
 * 隔离到独立嵌套配置类，并以 {@code @ConditionalOnClass(name = "...")}（字符串形式，绝不触发类加载）
 * 做门：SDK 缺席时对应嵌套配置整体跳过，其方法永不被内省，应用正常以 local/minio 启动。
 *
 * <p>同时负责两件「启动期裁定」（fail-closed，宁可起不来也不要带着缺口上线）：
 * <ol>
 *   <li>上传登记：{@code jffast.file.record.required=true}（默认）却没有可用 JdbcTemplate ⇒ 启动失败；</li>
 *   <li>签名密钥：非 dev/test profile 未显式配置 {@code jffast.file.sign.secret} ⇒ 启动失败
 *       （多实例密钥不一致会让验签随机失败，不允许静默降级成随机密钥）。</li>
 * </ol>
 *
 * @author jffast
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(FileStorageService.class)
public class JfFastFileAutoConfiguration {

    /** 非生产 profile 白名单：只有显式 dev / test 才放宽，其余（prod / staging / 未指定…）一律按生产严格 */
    private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("dev", "test");

    @Bean
    public FileOwnershipProperties fileOwnershipProperties(Environment environment) {
        FileOwnershipProperties properties = new FileOwnershipProperties();
        Binder binder = Binder.get(environment);
        // 只绑本类自己的两个子树，避免把 jffast.file.storage.* 一并吞进来
        binder.bind("jffast.file.sign", Bindable.ofInstance(properties.getSign()));
        binder.bind("jffast.file.record", Bindable.ofInstance(properties.getRecord()));
        resolveSignSecret(properties, environment);
        return properties;
    }

    @Bean
    @ConditionalOnMissingBean(FileStorageService.class)
    @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorageService(FileStorageProperties properties,
                                                      ObjectProvider<SysFileRecorder> recorder,
                                                      FileOwnershipProperties ownership) {
        return decorate(new LocalFileStorageService(properties), recorder, ownership);
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
                                                          ObjectProvider<SysFileRecorder> recorder,
                                                          FileOwnershipProperties ownership) {
            return decorate(new MinIOFileStorageService(minioClient, properties), recorder, ownership);
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
                    aliyunOss.getEndpoint(),
                    aliyunOss.getAccessKeyId(),
                    aliyunOss.getAccessKeySecret());
        }

        @Bean
        @ConditionalOnMissingBean(FileStorageService.class)
        @ConditionalOnProperty(prefix = "jffast.file.storage", name = "type", havingValue = "aliyun-oss", matchIfMissing = false)
        public FileStorageService aliyunOssFileStorageService(OSS ossClient, FileStorageProperties properties,
                                                              ObjectProvider<SysFileRecorder> recorder,
                                                              FileOwnershipProperties ownership) {
            return decorate(new AliyunOssFileStorageService(ossClient, properties), recorder, ownership);
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
                                                               ObjectProvider<SysFileRecorder> recorder,
                                                               FileOwnershipProperties ownership) {
            return decorate(new TencentCosFileStorageService(cosClient, properties), recorder, ownership);
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
        public SysFileRecorder sysFileRecorder(ObjectProvider<JdbcTemplate> jdbc,
                                                FileStorageProperties properties,
                                                ObjectProvider<TenantScopeBridge> tenantScopeBridge) {
            return new SysFileRecorder(jdbc.getIfAvailable(), properties, tenantScopeBridge);
        }

        /**
         * 记录读取（供不依赖 jffast-system 的服务按 id 读归属并换发签名 URL）。
         */
        @Bean
        public SysFileReader sysFileReader(ObjectProvider<JdbcTemplate> jdbc) {
            return new SysFileReader(jdbc.getIfAvailable());
        }
    }

    /**
     * 包装登记装饰器，并在「必须登记却登记不了」时直接让启动失败。
     */
    private static RecordingFileStorageService decorate(FileStorageService delegate,
                                                        ObjectProvider<SysFileRecorder> recorderProvider,
                                                        FileOwnershipProperties ownership) {
        SysFileRecorder recorder = recorderProvider.getIfAvailable();
        boolean required = ownership.getRecord().isRequired();
        if (required && (recorder == null || !recorder.isAvailable())) {
            throw new IllegalStateException(
                    "上传登记能力未启用：请配置 DataSource（或显式设置 jffast.file.record.required=false 关闭登记）。"
                            + "当前部署无法保证「有上传即有归属记录」，为避免产生不可追溯的孤儿文件，启动中止。");
        }
        return new RecordingFileStorageService(delegate, recorder, required);
    }

    /**
     * 签名密钥裁定：显式配置优先；非 dev/test 环境缺省 ⇒ 启动失败；dev/test 缺省 ⇒ 生成一次性随机密钥。
     */
    private static void resolveSignSecret(FileOwnershipProperties properties, Environment environment) {
        if (StrUtil.isNotBlank(properties.getSign().getSecret())) {
            return;
        }
        if (!isNonProduction(environment)) {
            throw new IllegalStateException(
                    "生产环境必须显式配置文件签名密钥 jffast.file.sign.secret："
                            + "多实例部署时各实例必须一致，否则验签会随机失败。"
                            + "可通过环境变量 JFFAST_FILE_SIGN_SECRET 注入。");
        }
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        properties.getSign().setSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(buf));
        log.warn("未配置 jffast.file.sign.secret，已生成一次性随机签名密钥（仅存在于当前进程内存，重启即失效）。"
                + "仅可用于本地开发联调；生产/集群必须显式配置同一密钥。");
    }

    /** 生产判定口径：除显式 dev / test 外一律按生产严格（空 profile 也算生产） */
    private static boolean isNonProduction(Environment environment) {
        String[] profiles = environment.getActiveProfiles();
        for (String profile : profiles) {
            if (NON_PRODUCTION_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }
}
