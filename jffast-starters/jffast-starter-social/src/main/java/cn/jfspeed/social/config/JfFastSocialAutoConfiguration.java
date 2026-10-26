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
package cn.jfspeed.social.config;

import cn.jfspeed.social.mapper.SocialBindMapper;
import cn.jfspeed.social.provider.GiteeProvider;
import cn.jfspeed.social.provider.GithubProvider;
import cn.jfspeed.social.provider.MicrosoftProvider;
import cn.jfspeed.social.provider.SocialLoginProvider;
import cn.jfspeed.social.service.SocialLoginService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(SocialLoginService.class)
@EnableConfigurationProperties(SocialProperties.class)
@ConditionalOnProperty(name = "jffast.social.enabled", havingValue = "true")
public class JfFastSocialAutoConfiguration {

    @Bean("githubProvider")
    @ConditionalOnMissingBean(name = "githubProvider")
    @ConditionalOnProperty(name = "jffast.social.providers.github.client-id")
    public GithubProvider githubProvider(SocialProperties props) {
        return new GithubProvider(props.getProviders().get("github"));
    }

    @Bean("giteeProvider")
    @ConditionalOnMissingBean(name = "giteeProvider")
    @ConditionalOnProperty(name = "jffast.social.providers.gitee.client-id")
    public GiteeProvider giteeProvider(SocialProperties props) {
        return new GiteeProvider(props.getProviders().get("gitee"));
    }

    @Bean("microsoftProvider")
    @ConditionalOnMissingBean(name = "microsoftProvider")
    @ConditionalOnProperty(name = "jffast.social.providers.microsoft.client-id")
    public MicrosoftProvider microsoftProvider(SocialProperties props) {
        return new MicrosoftProvider(props.getProviders().get("microsoft"));
    }

    @Bean
    @ConditionalOnMissingBean
    public SocialLoginService socialLoginService(SocialProperties socialProperties,
                                                 SocialBindMapper socialBindMapper,
                                                 ObjectProvider<SocialLoginProvider> providers) {
        return new SocialLoginService(socialProperties, socialBindMapper, providers.orderedStream().toList());
    }
}
