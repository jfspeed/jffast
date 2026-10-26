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
package cn.jfspeed.log.aspect;

import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.log.annotation.SLog;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class JfFastLogAutoConfiguration {

    /**
     * {@link ClientIpResolver} 由 {@code jffast-starter-security} 注册（那里才读得到
     * {@code jffast.security.trusted-proxies}）。本 Starter 对它是 optional 依赖，
     * 因此用 {@link ObjectProvider} 可选取用：取不到就退化为「只认 TCP 对端地址」的空列表实例。
     * <p>
     * 不能图省事直接注入 {@code ClientIpResolver}：那样在「只打日志、不装配安全 Starter」的
     * 部署形态下会因为缺 Bean 而启动失败，而日志本身并不依赖鉴权体系。
     */
    private static ClientIpResolver resolveClientIp(ObjectProvider<ClientIpResolver> provider) {
        return provider.getIfAvailable(() -> new ClientIpResolver(List.of()));
    }

    @Bean
    @ConditionalOnMissingBean
    public LogAspect logAspect(ObjectProvider<ClientIpResolver> clientIpResolver) {
        return new LogAspect(resolveClientIp(clientIpResolver));
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorLogAspect errorLogAspect(ObjectProvider<ClientIpResolver> clientIpResolver) {
        return new ErrorLogAspect(resolveClientIp(clientIpResolver));
    }

}
