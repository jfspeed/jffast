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
package cn.jfspeed.oauth2.client.provider;

import cn.jfspeed.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth2 令牌 HTTP 工具：使用 JDK HttpClient 发送表单请求，无额外依赖。
 * <p>
 * 令牌接口调用失败（非 2xx 或异常）统一抛 {@link BusinessException}，便于上层 fail-fast。
 *
 * @author jffast
 */
@Slf4j
public final class OAuth2HttpUtil {

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded; charset=UTF-8";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private OAuth2HttpUtil() {
    }

    /** 发送 application/x-www-form-urlencoded POST，返回响应体字符串 */
    public static String postForm(String url, Map<String, String> form, Map<String, String> headers) {
        String body = encode(form);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (headers != null) {
                headers.forEach(b::header);
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new BusinessException("OAuth2 令牌请求失败 HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
            }
            return resp.body();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OAuth2 令牌请求异常 url={}", url, e);
            throw new BusinessException("OAuth2 令牌请求异常: " + e.getMessage());
        }
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** 便捷：构造 client_secret_post 的表单认证字段 */
    public static Map<String, String> basicForm(String clientId, String clientSecret) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("client_id", clientId);
        m.put("client_secret", clientSecret);
        return m;
    }
}