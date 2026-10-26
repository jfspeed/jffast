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
package cn.jfspeed.mybatis.datasource;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;

/**
 * 动态数据源自动装配（jffast.datasource.dynamic.enabled=true 时生效）
 * <p>
 * 须先于 Spring Boot DataSourceAutoConfiguration 注册，让其按 @ConditionalOnMissingBean 退位；
 * 未开启时完全不介入，单数据源行为不变。
 *
 * @author jffast
 */
@AutoConfiguration
@AutoConfigureBefore(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "jffast.datasource.dynamic", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DynamicDataSourceProperties.class)
public class JfFastDynamicDataSourceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DynamicDataSource dynamicDataSource(DynamicDataSourceProperties properties) {
        Map<String, DynamicDataSourceProperties.DataSource> configured = properties.getDatasource();
        if (configured.isEmpty() || !configured.containsKey(properties.getPrimary())) {
            throw new IllegalStateException(
                    "jffast.datasource.dynamic 开启后必须配置数据源，且 primary=" + properties.getPrimary() + " 必须存在");
        }

        Map<Object, Object> targetDataSources = new HashMap<>();
        for (Map.Entry<String, DynamicDataSourceProperties.DataSource> entry : configured.entrySet()) {
            DynamicDataSourceProperties.DataSource cfg = entry.getValue();
            DataSource ds = DataSourceBuilder.create()
                    .url(cfg.getUrl())
                    .driverClassName(cfg.getDriverClassName())
                    .username(cfg.getUsername())
                    .password(cfg.getPassword())
                    .build();
            targetDataSources.put(entry.getKey(), ds);
        }

        DynamicDataSource routingDataSource = new DynamicDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(targetDataSources.get(properties.getPrimary()));
        return routingDataSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicDataSourceAspect dynamicDataSourceAspect() {
        return new DynamicDataSourceAspect();
    }
}
