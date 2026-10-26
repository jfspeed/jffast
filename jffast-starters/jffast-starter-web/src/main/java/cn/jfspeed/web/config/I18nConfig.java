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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.Locale;

/**
 * 国际化配置（Web 层）。
 *
 * <p><b>这里刻意不再定义 MessageSource。</b>本类原先与 {@code jffast-common} 的
 * {@code MessageSourceConfig#messageSource()} 定义了**同名 Bean**，两者都指向同一份
 * {@code classpath:messages} 资源，属于纯粹重复。当两个模块同时出现在 classpath 上
 * （本框架的用法正是如此：应用以 {@code @SpringBootApplication(scanBasePackages = "cn.jfspeed")}
 * 全包扫描，两个 {@code @Configuration} 都会被扫到），Spring 会因
 * {@code allow-bean-definition-overriding=false}（Boot 2.1+ 的默认值）抛
 * {@code BeanDefinitionOverrideException}，**应用完全无法启动**。
 *
 * <p>此类问题只有端到端启动实测才暴露（编译期完全看不出来，静态检查也发现不了）。
 * 保留 {@code jffast-common} 那份：它所在的模块更底层、恒在 classpath 上，且
 * {@code messages*.properties} 资源本身就放在该模块里。
 *
 * @author jffast
 */
@Configuration
public class I18nConfig {

    /**
     * 语言解析器（基于 Accept-Language 头）
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.CHINA);
        return resolver;
    }
}