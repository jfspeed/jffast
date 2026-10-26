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
package cn.jfspeed.web.filter;

import cn.jfspeed.web.config.WebProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

/**
 * XSS 过滤器：包装请求做参数/请求头/JSON 请求体清洗。
 *
 * <p>{@code jffast.web.xss.exclude-urls} 中的 URL 直接放行（精确匹配或 {@code *} 前缀匹配），
 * 供携带代码/模板且无法按字段豁免的接口整体跳过清洗。</p>
 *
 * @author jffast
 */
@Slf4j
@Order(1)
public class XssFilter implements Filter {

    private final WebProperties.Xss config;

    public XssFilter() {
        this(new WebProperties.Xss());
    }

    public XssFilter(WebProperties.Xss config) {
        this.config = config;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("XSS过滤器初始化完成");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (isExcluded(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        XssHttpServletRequestWrapper xssRequest = new XssHttpServletRequestWrapper(
                httpRequest, config.isJsonClean(), config.getJsonSkipFieldsSet());
        chain.doFilter(xssRequest, response);
    }

    /** 排除 URL 匹配：精确匹配，或配置以 {@code *} 结尾时做前缀匹配 */
    private boolean isExcluded(String uri) {
        List<String> excludes = config.getExcludeUrls();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        for (String pattern : excludes) {
            if (pattern == null || pattern.isEmpty()) {
                continue;
            }
            if (pattern.endsWith("*") ? uri.startsWith(pattern.substring(0, pattern.length() - 1))
                    : uri.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        log.info("XSS过滤器销毁");
    }
}
