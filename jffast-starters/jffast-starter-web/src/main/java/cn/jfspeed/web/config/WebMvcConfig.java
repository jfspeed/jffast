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

import cn.jfspeed.common.web.util.IPUtil;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.context.support.ServletRequestHandledEvent;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.Filter;
import java.io.File;
import java.nio.file.Paths;

/**
 * @author jffast
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 本地文件存储根目录，与 {@code FileStorageProperties} 的 {@code jffast.file.storage.local.path}
     * （默认 {@code upload}）保持一致，确保静态映射与实际落盘路径一致。
     */
    @org.springframework.beans.factory.annotation.Value("${jffast.file.storage.local.path:upload}")
    private String uploadBasePath;
    // 监听请求处理完成事件
    @EventListener(ServletRequestHandledEvent.class)
    public void onServletRequestHandled(ServletRequestHandledEvent event) {
        // 过滤状态码*404（路径不存在）或 405（方法不允许，如 POST 访问 GET 接口）的请求
        int statusCode = event.getStatusCode();
        if (statusCode == 404) {
            // 打印关键信息：请求路径、请求方法、状态码、客户端 IP
            log.warn("404请求触发 -> 路径: {}, 方法: {}, 状态码: {}, 客户端IP: {}",
                    event.getRequestUrl(), // 核心：请求接口地址（含参数，如 /api/user?id=1*
                    event.getMethod(),
                    statusCode,
                    event.getClientAddress());
        }
    }

    // 对Swagger的API文档接口使用默认转换器（避免NON_NULL影响文档结构*
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地文件存储访问映射：/upload/** 直接对外提供（与 jffast.file.storage.local.access-path=/upload 对应）
        // 绝对路径避免相对路径解析不一致导致上传后 404
        File baseDir = Paths.get(uploadBasePath).toAbsolutePath().normalize().toFile();
        registry.addResourceHandler("/upload/**").addResourceLocations(baseDir.toURI().toString());
        /** 历史 Knife4j 入口占位（当前无 knife4j 静态资源，404；官方适配 Boot 4 后回切时恢复） */
        registry.addResourceHandler("/doc.html").addResourceLocations("classpath:/META-INF/resources/");
        /** 公共部分内容 */
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    /**
     * /upload/** 静态资源响应头加固：上传目录匿名可访问，若被同源内联打开，
     * 恶意文件可能执行脚本（存储型 XSS）。此处兜底：
     * <ul>
     *   <li>{@code X-Content-Type-Options: nosniff} —— 阻止浏览器把 text/plain 等嗅探成 HTML 执行</li>
     *   <li>{@code Content-Security-Policy: sandbox} —— 直接访问 SVG/HTML 等资源时禁用脚本</li>
     * </ul>
     * 与 {@code LocalFileStorageService} 的上传扩展名拒绝共同构成两层防护。
     */
    @Bean
    public FilterRegistrationBean<Filter> uploadSecurityHeaderFilter() {
        Filter filter = (request, response, chain) -> {
            if (response instanceof jakarta.servlet.http.HttpServletResponse httpResponse) {
                httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                httpResponse.setHeader("Content-Security-Policy", "sandbox");
            }
            chain.doFilter(request, response);
        };
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/upload/*");
        registration.setName("uploadSecurityHeaderFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    /**
     * 允许跨域调用的过滤器。
     * 默认只放行 localhost（开发场景）；生产请通过 {@code jffast.web.cors.allowed-origin-patterns}
     * 配置真实前端域名，或关闭开关由网关/反向代理处理跨域。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "jffast.web.cors", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CorsFilter corsFilter(
            @org.springframework.beans.factory.annotation.Value("${jffast.web.cors.allowed-origin-patterns:http://localhost:*,https://localhost:*}")
            String allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        for (String pattern : allowedOriginPatterns.split(",")) {
            String p = pattern.trim();
            if (!p.isEmpty()) {
                config.addAllowedOriginPattern(p);
            }
        }
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    // 说明：此处原先还有一个 `addCorsMappings(CorsRegistry)`，已删除。
    // 它与上面的 corsFilter 是**两套并存**的 CORS 配置，且允许来源硬编码为 localhost——
    // 即使按文档把 jffast.web.cors.allowed-origin-patterns 改成生产域名，那条硬编码规则
    // 仍会把来源限制在 localhost，导致「配置改了却不生效」。统一由 corsFilter + 配置项处理。

    // 说明：此处原先有一个 `@Bean public LoginUtil loginUtil()`，已删除。
    // LoginUtil 是**纯静态工具类**（全仓没有任何地方注入或实例调用它，全是 LoginUtil.xxx()），
    // 注册成 Bean 毫无意义，却会带来一个致命副作用：LoginUtil 的字节码 catch 子句引用了
    // cn.dev33.satoken.exception.SaTokenContextException，而 sa-token-core 在本 starter 里是
    // provided —— 于是「引入本 starter 但没引 Sa-Token」的应用会在创建这个 Bean 时
    // 抛 NoClassDefFoundError: cn/dev33/satoken/exception/SaTokenContextException，整个应用起不来。
    // （2026-09-21 运行时验收实测：修掉 GlobalExceptionConfig 后紧接着就撞上这一处。）

    @Bean
    public IdGeneratorOptions initIDGen() {
        int workerId = resolveWorkerId();
        IdGeneratorOptions options = new IdGeneratorOptions((short) workerId);
        options.WorkerIdBitLength = 6; // 支持 16 个节点
        options.SeqBitLength = 10;      // 每毫秒最多生成 64 个 ID
        // options.BaseTime = ...;      // 可选，默认 2020-01-01  6,10可支持年数（JS安全 2^37 ms ≈ 69 年

        YitIdHelper.setIdGenerator(options);
        return options;
    }

    /**
     * 解析 WorkerId，优先级：
     * 1. 环境变量 WORKER_ID（手动指定，适用于非 K8s 或 Deployment）
     * 2. K8s StatefulSet Pod 序号（从 hostname 提取，如 learning-0 → 0）
     * 3. 本机 IP CRC32 哈希取模（降级方案）
     */
    private int resolveWorkerId() {
        // 1. 环境变量优先
        String envWorkerId = System.getenv("WORKER_ID");
        if (envWorkerId != null && !envWorkerId.isEmpty()) {
            return Integer.parseInt(envWorkerId);
        }

        // 2. K8s StatefulSet：从 hostname 提取序号
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            int lastDash = hostname.lastIndexOf('-');
            if (lastDash >= 0) {
                return Integer.parseInt(hostname.substring(lastDash + 1));
            }
        } catch (Exception e) {
            // hostname 非 StatefulSet 命名（无「-序号」后缀）或解析失败：降级为 IP 哈希，属预期分支
            log.debug("从 hostname 解析 StatefulSet 序号失败，降级为 IP 哈希：{}", e.getMessage());
        }

        // 3. 降级：IP CRC32 哈希
        String localIp = IPUtil.getLocalIp();
        if (localIp == null || localIp.isEmpty()) {
            localIp = "127.0.0.1";
        }
        return IPUtil.getWorkerIdByIp(localIp, 6);
    }
}