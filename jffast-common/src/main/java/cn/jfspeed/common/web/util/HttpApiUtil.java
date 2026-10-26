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
package cn.jfspeed.common.web.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Map;

import cn.hutool.core.date.DateUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class HttpApiUtil {

    private static final int DEFAULT_TIMEOUT = 30000;

    public static String get(String url) {
        return get(url, null, DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, Object> params) {
        return get(url, params, DEFAULT_TIMEOUT);
    }

    public static String get(String url, Map<String, Object> params, int timeout) {
        HttpRequest request = HttpUtil.createGet(url);
        if (params != null && !params.isEmpty()) {
            request.form(params);
        }
        return executeRequest(request.timeout(timeout));
    }

    public static String postJson(String url, JSONObject jsonParam) {
        return postJson(url, jsonParam, DEFAULT_TIMEOUT);
    }

    public static String postJson(String url, JSONObject jsonParam, Map<String, String> headers) {
        return postJson(url, jsonParam, DEFAULT_TIMEOUT, headers);
    }

    public static String postJson(String url, JSONObject jsonParam, int timeout) {
        return postJson(url, jsonParam, timeout, null);
    }

    public static String postJson(String url, JSONObject jsonParam, int timeout, Map<String, String> headers) {
        byte[] gbkBytes;
        try {
            gbkBytes = jsonParam.toString().getBytes("gb2312");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        HttpRequest header = HttpUtil.createPost(url)
                .header("Content-Type", "application/json; charset=UTF-8");
        if (headers != null && !headers.isEmpty()) {
            for (String key : headers.keySet()) {
                header.header(key, headers.get(key));
            }
        }
        HttpRequest request = header.body(gbkBytes);
        return executeRequest(request.timeout(timeout));
    }

    public static String postJsonUtf8(String url, JSONObject jsonParam, int timeout, Map<String, String> headers) {
        HttpRequest header = HttpUtil.createPost(url)
                .header("Content-Type", "application/json; charset=UTF-8");
        if (headers != null && !headers.isEmpty()) {
            for (String key : headers.keySet()) {
                header.header(key, headers.get(key));
            }
        }
        HttpRequest request = header.body(jsonParam.toString());
        return executeRequestUtf8(request.timeout(timeout));
    }

    public static String postForm(String url, Map<String, Object> params) {
        return postForm(url, params, DEFAULT_TIMEOUT);
    }

    public static String postForm(String url, Map<String, Object> params, int timeout) {
        HttpRequest request = HttpUtil.createPost(url);
        if (params != null && !params.isEmpty()) {
            request.form(params);
        }
        return executeRequest(request.timeout(timeout));
    }

    public static String postFile(String url, File file, String paramName) {
        return postFile(url, null, file, paramName, DEFAULT_TIMEOUT);
    }

    public static String postFile(String url, Map<String, Object> params, File file, String paramName) {
        return postFile(url, params, file, paramName, DEFAULT_TIMEOUT);
    }

    public static String postFile(String url, Map<String, Object> params, File file, String paramName, int timeout) {
        HttpRequest request = HttpUtil.createPost(url);
        if (params != null && !params.isEmpty()) {
            request.form(params);
        }
        request.form(paramName, file);
        return executeRequest(request.timeout(timeout));
    }

    public static String putJson(String url, JSONObject jsonParam) {
        return putJson(url, jsonParam, DEFAULT_TIMEOUT);
    }

    public static String putJson(String url, JSONObject jsonParam, int timeout) {
        HttpRequest request = HttpRequest.of(url)
                .method(Method.PUT)
                .body(jsonParam.toString())
                .header("Content-Type", "application/json; charset=UTF-8");
        return executeRequest(request.timeout(timeout));
    }

    public static String putForm(String url, Map<String, Object> params) {
        return putForm(url, params, DEFAULT_TIMEOUT);
    }

    public static String putForm(String url, Map<String, Object> params, int timeout) {
        HttpRequest request = HttpRequest.of(url)
                .method(Method.PUT);
        if (params != null && !params.isEmpty()) {
            request.form(params);
        }
        return executeRequest(request.timeout(timeout));
    }

    public static String delete(String url) {
        return delete(url, null, DEFAULT_TIMEOUT);
    }

    public static String delete(String url, Map<String, Object> params) {
        return delete(url, params, DEFAULT_TIMEOUT);
    }

    public static String delete(String url, Map<String, Object> params, int timeout) {
        HttpRequest request = HttpRequest.of(url)
                .method(Method.DELETE);
        if (params != null && !params.isEmpty()) {
            request.form(params);
        }
        return executeRequest(request.timeout(timeout));
    }

    private static String executeRequest(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            String charset = "gb2312";
            return new String(response.bodyBytes(), charset);
        } catch (Exception e) {
            throw new RuntimeException("HTTP请求执行失败: " + request.getUrl() + " | " + e.getMessage(), e);
        }
    }

    private static String executeRequestUtf8(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            String charset = "UTF-8";
            return new String(response.bodyBytes(), charset);
        } catch (Exception e) {
            throw new RuntimeException("HTTP请求执行失败: " + request.getUrl() + " | " + e.getMessage(), e);
        }
    }
}
