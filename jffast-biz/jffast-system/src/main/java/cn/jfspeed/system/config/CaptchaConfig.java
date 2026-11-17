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
package cn.jfspeed.system.config;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.TACBuilder;
import cloud.tianai.captcha.cache.impl.LocalCacheStore;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.constant.CommonConstant;
import cloud.tianai.captcha.generator.impl.transform.Base64ImageTransform;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.provider.ClassPathResourceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 登录滑块验证码（tianai-captcha）装配配置。
 *
 * <p>不用官方 springboot-starter：它基于 Spring Boot 2.2 的 {@code spring.factories}
 * 自动配置，在 Spring Boot 4 已不再生效。这里用 core 库的 {@link TACBuilder} 手动装配，
 * 得到线程安全的 {@link ImageCaptchaApplication} 单例。
 *
 * <p>缓存用 {@link LocalCacheStore}（本地 LRU + TTL），Base 单实例部署足够；
 * 多实例（Pro/Cloud）部署时 gen 与 check 可能落到不同实例，届时可覆写本 Bean 换 Redis CacheStore
 * ——这是有意留的扩展点（{@code @ConditionalOnMissingBean}），本轮不实现 Redis 版。
 *
 * <p>开关 {@code jffast.captcha.slider-enabled}：默认开启（matchIfMissing=true），
 * 设为 false 时本 Bean 不装配，登录逻辑据此回落纯图片验证码。
 *
 * @author jffast
 */
@Configuration
public class CaptchaConfig {

    /** 滑块验证码 Redis/本地缓存 key 前缀，避免与其他 tianai 应用串 key。 */
    private static final String CACHE_PREFIX = "jffast:tac:";

    /** 验证码有效期：60 秒，与现有图片验证码 TTL 一致。 */
    private static final long EXPIRE_MS = 60_000L;

    /** 滑块背景图数量：resource/1.jpg（SDK 内置猫图）+ 2~5.jpg（自然风景），同 tag 注册后由 SDK 随机取用。 */
    private static final int SLIDER_RESOURCE_COUNT = 5;

    /**
     * 装配滑块验证码应用单例。参数见 tianai-captcha 1.5.5 TACBuilder。
     *
     * @return 线程安全的验证码应用，全局单例
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "jffast.captcha", name = "slider-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ImageCaptchaApplication.class)
    public ImageCaptchaApplication imageCaptchaApplication() {
        TACBuilder builder = TACBuilder.builder()
                // 内置滑块/旋转/点选的**模板**（active.png / fixed.png）
                .addDefaultTemplate();
        // 内置**背景图**资源：addDefaultTemplate 只注册模板，不含背景图；
        // 不注册会导致 StandardSliderImageCaptchaGenerator 取背景图时空指针
        // （"随机获取资源错误，store中资源为空, type:SLIDER"）。
        // 注册多张同 DEFAULT_TAG 的背景图，SDK 的 requiredRandomGetResource(type, tag) 每次随机取一张。
        // 图片在 resources/META-INF/cut-image/resource/1~5.jpg，classpath 必定可读。
        for (int i = 1; i <= SLIDER_RESOURCE_COUNT; i++) {
            builder.addResource(CaptchaTypeConstant.SLIDER,
                    new Resource(ClassPathResourceProvider.NAME,
                            "META-INF/cut-image/resource/" + i + ".jpg", CommonConstant.DEFAULT_TAG));
        }
        return builder
                // prefix(type, expire ms ...)：默认过期时间
                .prefix(CACHE_PREFIX)
                .expire("default", EXPIRE_MS)
                // 本地缓存：初始容量/最大容量/每次清理数/过期时间
                .cached(16, 512, 32, EXPIRE_MS)
                // 图片以 base64 返回，前端 SDK 直接渲染，无需额外图片接口
                .setTransform(new Base64ImageTransform())
                .setCacheStore(new LocalCacheStore())
                .build();
    }
}
