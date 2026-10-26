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
package cn.jfspeed.security.config;

import cn.jfspeed.common.web.util.ClientIpResolver;
import cn.jfspeed.security.aspect.DataPermissionAspect;
import cn.jfspeed.security.aspect.DistributedLockAspect;
import cn.jfspeed.security.aspect.IdempotentAspect;
import cn.jfspeed.security.aspect.RateLimitAspect;
import cn.jfspeed.security.ratelimit.IpRateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(cn.dev33.satoken.stp.StpInterface.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class JfFastSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SaTokenConfig saTokenConfig(SecurityProperties securityProperties,
                                       ObjectProvider<InternalCallDetector> internalCallDetector,
                                       @org.springframework.beans.factory.annotation.Value("${jffast.swagger.enabled:true}") boolean swaggerEnabled) {
        return new SaTokenConfig(securityProperties, internalCallDetector, swaggerEnabled);
    }

    @Bean
    @ConditionalOnMissingBean
    public SaTokenStpInterfaceImpl saTokenStpInterfaceImpl() {
        return new SaTokenStpInterfaceImpl();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiterRegistry rateLimiterRegistry) {
        return new RateLimitAspect(rateLimiterRegistry);
    }

    /**
     * 真实客户端 IP 的统一解析入口。
     * <p>
     * 限流（{@link IpRateLimiter}）、操作/错误日志切面、商业版的 IP 白名单都调这个 Bean，
     * 于是「代理拓扑长什么样」只需在 {@code jffast.security.trusted-proxies} 配一次。
     * <p>
     * 类本身在 {@code jffast-common}（日志切面对本 Starter 是 optional 依赖，见那个类的 javadoc），
     * 本方法只负责把配置喂进去。依赖 Bean 的模块用 {@code ObjectProvider} 可选注入：
     * 未装配本 Starter 时兜底为「只认 TCP 对端地址」——刻意不退回可伪造的
     * {@code IPUtil#getIpAddress()}，否则安全场景会悄悄退回能被绕过的老路。
     */
    @Bean
    @ConditionalOnMissingBean
    public ClientIpResolver clientIpResolver(SecurityProperties securityProperties) {
        return new ClientIpResolver(securityProperties.getTrustedProxies());
    }

    /**
     * 按客户端 IP 的独立限流。默认启用（{@code jffast.security.login-ip-limit.enabled} /
     * {@code captcha-ip-limit.enabled} 缺省为 true），场景级开关关闭只是让该场景不计数，
     * Bean 仍在；控制层用 {@code ObjectProvider#getIfAvailable()} 取，取不到即跳过。
     */
    @Bean
    @ConditionalOnMissingBean
    public IpRateLimiter ipRateLimiter(ClientIpResolver clientIpResolver, SecurityProperties securityProperties) {
        return new IpRateLimiter(clientIpResolver, securityProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DistributedLockAspect distributedLockAspect() {
        return new DistributedLockAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public DataPermissionAspect dataPermissionAspect() {
        return new DataPermissionAspect();
    }

    @Bean
    @ConditionalOnMissingBean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect();
    }

    /**
     * Sa-Token 1.46.0 起 JSON 多态反序列化受全局类型白名单约束（SaJsonStrategy）。
     * 登录时写入 session 的业务实体必须在此注册，否则读取 session 时反序列化被拒，
     * 表现为「已登录但取不到用户」（异常被 LoginUtil 吞掉，静默降级为未登录）。
     * <p>
     * 必须用 {@link BeanFactoryPostProcessor} 注册：SaJsonStrategy 在 Sa-Token 组件
     * 初始化时即锁定白名单，晚于此的注册会抛「已初始化，无法再注册」。
     * 此时应走 Spring 环境而非 SecurityProperties Bean（BFPP 阶段属性尚未绑定）。
     */
    @Bean
    public static org.springframework.beans.factory.config.BeanFactoryPostProcessor saJsonAllowTypeRegistrar(
            org.springframework.core.env.Environment environment) {
        return beanFactory -> {
            java.util.List<String> types = org.springframework.boot.context.properties.bind.Binder
                    .get(environment)
                    .bind("jffast.security.sa-json-allow-types",
                            org.springframework.boot.context.properties.bind.Bindable.listOf(String.class))
                    .orElse(java.util.Collections.emptyList());
            for (String type : types) {
                if (type == null || type.isBlank()) {
                    continue;
                }
                try {
                    cn.dev33.satoken.strategy.SaJsonStrategy.instance.registerAllowType(Class.forName(type.trim()));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(
                            "jffast.security.sa-json-allow-types 配置的类不存在: " + type, e);
                }
            }
        };
    }

}
