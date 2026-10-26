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
package cn.jfspeed.oauth2.client.config;

import cn.jfspeed.oauth2.client.provider.GenericOAuth2Provider;
import cn.jfspeed.oauth2.client.provider.GiteeOAuth2Provider;
import cn.jfspeed.oauth2.client.provider.GithubOAuth2Provider;
import cn.jfspeed.oauth2.client.provider.MicrosoftOAuth2Provider;
import cn.jfspeed.oauth2.client.provider.OAuth2ClientProvider;
import cn.jfspeed.oauth2.client.service.OAuth2ClientConfigService;
import cn.jfspeed.oauth2.client.service.OAuth2ClientService;
import cn.jfspeed.oauth2.client.store.OAuth2ClientTokenStore;
import cn.jfspeed.oauth2.client.store.RedisOAuth2ClientTokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OAuth2 客户端 Starter 自动装配。
 * <p>
 * 在此按 {@code jffast.oauth2.client.enabled} 门控声明 provider / store / service 等业务 Bean；
 * 内置 github/gitee/microsoft 三个默认 provider 与一个配置驱动的通用兜底
 * {@code GenericOAuth2Provider}。业务方也可自行声明 {@link OAuth2ClientProvider} / 自定义
 * {@link OAuth2ClientTokenStore} Bean 覆盖默认实现（见各 @ConditionalOnMissingBean）。
 *
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(OAuth2ClientService.class)
@EnableConfigurationProperties(OAuth2ClientProperties.class)
@ConditionalOnProperty(name = "jffast.oauth2.client.enabled", havingValue = "true")
public class JfFastOAuth2ClientAutoConfiguration {

    /** 默认令牌缓存：复用系统内置 Redis */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2ClientTokenStore oauth2ClientTokenStore() {
        return new RedisOAuth2ClientTokenStore();
    }

    @Bean
    public OAuth2ClientProvider oauth2GithubProvider() {
        return new GithubOAuth2Provider();
    }

    @Bean
    public OAuth2ClientProvider oauth2GiteeProvider() {
        return new GiteeOAuth2Provider();
    }

    @Bean
    public OAuth2ClientProvider oauth2MicrosoftProvider() {
        return new MicrosoftOAuth2Provider();
    }

    @Bean
    public OAuth2ClientProvider oauth2GenericProvider() {
        return new GenericOAuth2Provider();
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2ClientConfigService oauth2ClientConfigService(
            cn.jfspeed.oauth2.client.mapper.OAuth2ClientConfigMapper mapper) {
        return new OAuth2ClientConfigService(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuth2ClientService oauth2ClientService(
            OAuth2ClientTokenStore store,
            OAuth2ClientConfigService configService,
            java.util.List<OAuth2ClientProvider> providers) {
        return new OAuth2ClientService(store, configService, providers);
    }
}