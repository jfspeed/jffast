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
package cn.jfspeed.common.web.domain.dto;

import cn.hutool.json.JSONObject;
import lombok.Data;

/**
 * @author jffast
 */
@Data
public class HttpResult {
    private int httpCode;
    private boolean success;
    private String body;
    private JSONObject jsonData;
    private String errorMessage;
    private String contentType;

    public HttpResult() {
    }

    public HttpResult(int httpCode, String body, String contentType) {
        this.httpCode = httpCode;
        this.body = body;
        this.contentType = contentType;
        this.success = isHttpSuccess(httpCode);

        if (body != null && cn.hutool.json.JSONUtil.isTypeJSON(body)) {
            try {
                this.jsonData = cn.hutool.json.JSONUtil.parseObj(body);
            } catch (Exception e) {
            }
        }

        if (!this.success) {
            this.errorMessage = buildErrorMessage(httpCode, body);
        }
    }

    private boolean isHttpSuccess(int httpCode) {
        return httpCode >= 200 && httpCode < 300;
    }

    private String buildErrorMessage(int httpCode, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP请求失败，状态码: ").append(httpCode);

        switch (httpCode) {
            case 400:
                sb.append(" (Bad Request - 请求参数错误)");
                break;
            case 401:
                sb.append(" (Unauthorized - 未授权)");
                break;
            case 403:
                sb.append(" (Forbidden - 禁止访问)");
                break;
            case 404:
                sb.append(" (Not Found - 资源不存在)");
                break;
            case 405:
                sb.append(" (Method Not Allowed - 方法不允许)");
                break;
            case 408:
                sb.append(" (Request Timeout - 请求超时)");
                break;
            case 500:
                sb.append(" (Internal Server Error - 服务器内部错误)");
                break;
            case 502:
                sb.append(" (Bad Gateway - 网关错误)");
                break;
            case 503:
                sb.append(" (Service Unavailable - 服务不可用)");
                break;
            case 504:
                sb.append(" (Gateway Timeout - 网关超时)");
                break;
            default:
                break;
        }

        if (body != null && !body.isEmpty()) {
            String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
            sb.append(", 响应内容: ").append(preview);
        }

        return sb.toString();
    }

    public Object getJsonField(String key) {
        if (jsonData != null) {
            return jsonData.get(key);
        }
        return null;
    }

    public String getJsonFieldStr(String key) {
        Object value = getJsonField(key);
        return value != null ? value.toString() : null;
    }

    public Integer getJsonFieldInt(String key) {
        Object value = getJsonField(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public boolean hasJsonField(String key) {
        return jsonData != null && jsonData.containsKey(key);
    }

    public static HttpResult success(int httpCode, String body, String contentType) {
        return new HttpResult(httpCode, body, contentType);
    }

    public static HttpResult failure(int httpCode, String body, String contentType) {
        return new HttpResult(httpCode, body, contentType);
    }

    @Override
    public String toString() {
        return "HttpResult{" +
                "httpCode=" + httpCode +
                ", success=" + success +
                ", body='" + (body != null && body.length() > 100 ? body.substring(0, 100) + "..." : body) + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", jsonData=" + jsonData +
                '}';
    }
}
