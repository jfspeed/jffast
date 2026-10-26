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
package cn.jfspeed.social.util;

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.Map;

/**
 * @author jffast
 */
@Slf4j
public class SocialHttpUtil {

    private static final WebClient WEB_CLIENT = WebClient.builder().build();

    public static String get(String url) {
        try {
            return WEB_CLIENT.get()
                    .uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "HTTP GET 请求失败: " + e.getMessage());
        }
    }

    public static String getWithAuth(String url, String accessToken) {
        try {
            return WEB_CLIENT.get()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "HTTP GET 请求失败: " + e.getMessage());
        }
    }

    public static String postForm(String url, Map<String, String> params) {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            params.forEach(formData::add);
            return WEB_CLIENT.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .bodyValue(formData)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "HTTP POST 请求失败: " + e.getMessage());
        }
    }

    /**
     * 携带自定义请求头的 GET。
     * <p>
     * 部分企业平台（如钉钉开放平台）不用 {@code Authorization: Bearer} 约定，
     * 而是通过专属请求头传令牌（如 {@code x-acs-dingtalk-access-token}）。
     */
    public static String getWithHeaders(String url, Map<String, String> headers) {
        try {
            return WEB_CLIENT.get()
                    .uri(URI.create(url))
                    .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "HTTP GET 请求失败: " + e.getMessage());
        }
    }

    /**
     * 提交 JSON 体的 POST。
     *
     * @param jsonBody JSON 字符串（调用方负责序列化，便于不同平台拼装各自字段）
     */
    public static String postJson(String url, String jsonBody, Map<String, String> headers) {
        try {
            return WEB_CLIENT.post()
                    .uri(URI.create(url))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                    .bodyValue(jsonBody)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "HTTP POST 请求失败: " + e.getMessage());
        }
    }
}
