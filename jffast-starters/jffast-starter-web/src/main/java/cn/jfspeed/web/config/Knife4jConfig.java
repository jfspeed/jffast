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
package cn.jfspeed.web.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（springdoc 原生；类名保留 Knife4j 以便官方适配 Boot 4 后一键回切，见 BOM 注释）。
 * 由 {@code jffast.swagger.enabled} 控制（默认开启；生产 profile 设为 false 时不装配）。
 *
 * @author jffast
 */
@Configuration
@ConditionalOnProperty(prefix = "jffast.swagger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Knife4jConfig {

    @Value("${app.name:jffast}")
    private String appName;

    @Bean
    public OpenAPI springShopOpenAPI() {
        return new OpenAPI()
                .info(new Info().title(appName)
                        .description(appName + " API文档")
                        .version("v1.0")
                        .contact(new Contact().name("admin").url("https://jfspeed.cn")))
                .externalDocs(new ExternalDocumentation()
                        .description(appName + " 补充文档")
                        .url("https://jfspeed.cn"));
    }

    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("系统管理")
                .pathsToMatch("/sys/**")
                .build();
    }

    @Bean
    public GroupedOpenApi demoApi() {
        return GroupedOpenApi.builder()
                .group("示例模块")
                .pathsToMatch("/demo/**")
                .build();
    }

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("认证授权")
                .pathsToMatch("/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi flowApi() {
        return GroupedOpenApi.builder()
                .group("工作流")
                .pathsToMatch("/flow/**", "/wf/**")
                .build();
    }

}
