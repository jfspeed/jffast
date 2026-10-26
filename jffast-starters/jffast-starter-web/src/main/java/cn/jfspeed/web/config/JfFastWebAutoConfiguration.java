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

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.DispatcherServlet;

import cn.jfspeed.web.filter.RequestWrapperFilter;
import cn.jfspeed.web.filter.TraceIdFilter;
import cn.jfspeed.web.filter.XssFilter;

/**
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@EnableConfigurationProperties(WebProperties.class)
@PropertySource(value = "classpath:application-web-actuator.yml", factory = YamlPropertySourceFactory.class)
@PropertySource(value = "classpath:application-web-tracing.yml", factory = YamlPropertySourceFactory.class)
@PropertySource(value = "classpath:application-web-logging.yml", factory = YamlPropertySourceFactory.class)
public class JfFastWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionConfig globalExceptionConfig() {
        return new GlobalExceptionConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public JacksonGlobalConfig jacksonGlobalConfig() {
        return new JacksonGlobalConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebMvcConfig webMvcConfig() {
        return new WebMvcConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "jffast.swagger", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Knife4jConfig knife4jConfig() {
        return new Knife4jConfig();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jffast.web.trace-id", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TraceIdFilter traceIdFilter(ObjectProvider<Tracer> tracerProvider) {
        return new TraceIdFilter(tracerProvider);
    }

    @Bean
    @ConditionalOnProperty(prefix = "jffast.web.xss", name = "enabled", havingValue = "true", matchIfMissing = true)
    public XssFilter xssFilter(WebProperties properties) {
        return new XssFilter(properties.getXss());
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestWrapperFilter requestWrapperFilter() {
        return new RequestWrapperFilter();
    }

}
