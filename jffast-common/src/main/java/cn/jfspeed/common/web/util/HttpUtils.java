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

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.StreamProgress;
import cn.hutool.core.net.RFC3986;
import cn.hutool.core.net.url.UrlQuery;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.*;
import cn.hutool.http.*;
import cn.hutool.http.cookie.GlobalCookieManager;
import cn.hutool.http.server.SimpleServer;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.domain.dto.HttpResult;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author jffast
 */
@Slf4j
public class HttpUtils {
    public static final Pattern CHARSET_PATTERN = Pattern.compile("charset\\s*=\\s*([a-z0-9-]*)", 2);
    public static final Pattern META_CHARSET_PATTERN = Pattern.compile("<meta[^>]*?charset\\s*=\\s*['\"]?([a-z0-9-]*)", 2);

    public static final int DEFAULT_GET_TIMEOUT = 5000;
    public static final int DEFAULT_POST_TIMEOUT = 10000;
    public static final int DEFAULT_PUT_TIMEOUT = 10000;
    public static final int DEFAULT_DELETE_TIMEOUT = 5000;
    public static final int DEFAULT_UPLOAD_TIMEOUT = 30000;

    public HttpUtils() {
    }

    // ========== 以下为原生HttpUtil的所有方法（完全复刻，保证原有功能不变） ==========
    public static boolean isHttps(String url) {
        return url.toLowerCase().startsWith("https:");
    }

    public static boolean isHttp(String url) {
        return url.toLowerCase().startsWith("http:");
    }

    public static HttpRequest createRequest(Method method, String url) {
        return HttpRequest.of(url).method(method);
    }

    public static HttpRequest createGet(String url) {
        return createGet(url, false);
    }

    public static HttpRequest createGet(String url, boolean isFollowRedirects) {
        return HttpRequest.get(url).setFollowRedirects(isFollowRedirects);
    }

    public static HttpRequest createPost(String url) {
        return HttpRequest.post(url);
    }

    public static String get(String urlString, Charset customCharset) {
        HttpResponse response = ((HttpRequest)HttpRequest.get(urlString).charset(customCharset)).execute();
        int status = response.getStatus();
        String body = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return body;
    }

    public static String get(String urlString) {
        return get(urlString, HttpGlobalConfig.getTimeout());
    }

    public static String get(String urlString, int timeout) {
        HttpResponse response = HttpRequest.get(urlString).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return body;
    }

    public static String get(String urlString, Map<String, Object> paramMap) {
        HttpResponse response = HttpRequest.get(urlString).form(paramMap).execute();
        int status = response.getStatus();
        String body = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return body;
    }

    public static String get(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResponse response = HttpRequest.get(urlString).form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return body;
    }

    public static String post(String urlString, Map<String, Object> paramMap) {
        return post(urlString, paramMap, HttpGlobalConfig.getTimeout());
    }

    public static String post(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResponse response = HttpRequest.post(urlString).form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return body;
    }

    public static String post(String urlString, String body) {
        return post(urlString, body, HttpGlobalConfig.getTimeout());
    }

    public static String post(String urlString, String body, int timeout) {
        HttpResponse response = HttpRequest.post(urlString).timeout(timeout).body(body).execute();
        int status = response.getStatus();
        String responseBody = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return responseBody;
    }

    public static String downloadString(String url, String customCharsetName) {
        return downloadString(url, CharsetUtil.charset(customCharsetName), (StreamProgress)null);
    }

    public static String downloadString(String url, Charset customCharset) {
        return downloadString(url, customCharset, (StreamProgress)null);
    }

    public static String downloadString(String url, Charset customCharset, StreamProgress streamPress) {
        return HttpDownloader.downloadString(url, customCharset, streamPress);
    }

    public static long downloadFile(String url, String dest) {
        return downloadFile(url, FileUtil.file(dest));
    }

    public static long downloadFile(String url, File destFile) {
        return downloadFile(url, destFile, (StreamProgress)null);
    }

    public static long downloadFile(String url, File destFile, int timeout) {
        return downloadFile(url, destFile, timeout, (StreamProgress)null);
    }

    public static long downloadFile(String url, File destFile, StreamProgress streamProgress) {
        return downloadFile(url, destFile, -1, streamProgress);
    }

    public static long downloadFile(String url, File destFile, int timeout, StreamProgress streamProgress) {
        return HttpDownloader.downloadFile(url, destFile, timeout, streamProgress);
    }

    public static File downloadFileFromUrl(String url, String dest) {
        return downloadFileFromUrl(url, FileUtil.file(dest));
    }

    public static File downloadFileFromUrl(String url, File destFile) {
        return downloadFileFromUrl(url, destFile, (StreamProgress)null);
    }

    public static File downloadFileFromUrl(String url, File destFile, int timeout) {
        return downloadFileFromUrl(url, destFile, timeout, (StreamProgress)null);
    }

    public static File downloadFileFromUrl(String url, File destFile, StreamProgress streamProgress) {
        return downloadFileFromUrl(url, destFile, -1, streamProgress);
    }

    public static File downloadFileFromUrl(String url, File destFile, int timeout, StreamProgress streamProgress) {
        return HttpDownloader.downloadForFile(url, destFile, timeout, streamProgress);
    }

    public static long download(String url, OutputStream out, boolean isCloseOut) {
        return download(url, out, isCloseOut, (StreamProgress)null);
    }

    public static long download(String url, OutputStream out, boolean isCloseOut, StreamProgress streamProgress) {
        return HttpDownloader.download(url, out, isCloseOut, streamProgress);
    }

    public static byte[] downloadBytes(String url) {
        return HttpDownloader.downloadBytes(url);
    }

    public static String toParams(Map<String, ?> paramMap) {
        return toParams(paramMap, CharsetUtil.CHARSET_UTF_8);
    }

    public static String toParams(Map<String, ?> paramMap, Charset charset) {
        return toParams(paramMap, charset, false);
    }

    public static String toParams(Map<String, ?> paramMap, Charset charset, boolean isFormUrlEncoded) {
        return UrlQuery.of(paramMap, isFormUrlEncoded).build(charset);
    }

    public static String encodeParams(String urlWithParams, Charset charset) {
        if (StrUtil.isBlank(urlWithParams)) {
            return "";
        } else {
            String urlPart = null;
            int pathEndPos = urlWithParams.indexOf(63);
            String paramPart;
            if (pathEndPos > -1) {
                urlPart = StrUtil.subPre(urlWithParams, pathEndPos);
                paramPart = StrUtil.subSuf(urlWithParams, pathEndPos + 1);
                if (StrUtil.isBlank(paramPart)) {
                    return urlPart;
                }
            } else {
                if (!StrUtil.contains(urlWithParams, '=')) {
                    return urlWithParams;
                }

                paramPart = urlWithParams;
            }

            paramPart = normalizeParams(paramPart, charset);
            return StrUtil.isBlank(urlPart) ? paramPart : urlPart + "?" + paramPart;
        }
    }

    public static String normalizeParams(String paramPart, Charset charset) {
        if (StrUtil.isEmpty(paramPart)) {
            return paramPart;
        } else {
            StrBuilder builder = StrBuilder.create(paramPart.length() + 16);
            int len = paramPart.length();
            String name = null;
            int pos = 0;

            int i;
            for(i = 0; i < len; ++i) {
                char c = paramPart.charAt(i);
                if (c == '=') {
                    if (null == name) {
                        name = pos == i ? "" : paramPart.substring(pos, i);
                        pos = i + 1;
                    }
                } else if (c == '&') {
                    if (pos != i) {
                        if (null == name) {
                            name = paramPart.substring(pos, i);
                            builder.append(RFC3986.QUERY_PARAM_NAME.encode(name, charset)).append('=');
                        } else {
                            builder.append(RFC3986.QUERY_PARAM_NAME.encode(name, charset)).append('=').append(RFC3986.QUERY_PARAM_VALUE.encode(paramPart.substring(pos, i), charset)).append('&');
                        }

                        name = null;
                    }

                    pos = i + 1;
                }
            }

            if (null != name) {
                builder.append(URLUtil.encodeQuery(name, charset)).append('=');
            }

            if (pos != i) {
                if (null == name && pos > 0) {
                    builder.append('=');
                }

                builder.append(URLUtil.encodeQuery(paramPart.substring(pos, i), charset));
            }

            int lastIndex = builder.length() - 1;
            if ('&' == builder.charAt(lastIndex)) {
                builder.delTo(lastIndex);
            }

            return builder.toString();
        }
    }

    public static Map<String, String> decodeParamMap(String paramsStr, Charset charset) {
        Map<CharSequence, CharSequence> queryMap = UrlQuery.of(paramsStr, charset).getQueryMap();
        return cn.hutool.core.map.MapUtil.isEmpty(queryMap) ? cn.hutool.core.map.MapUtil.empty() : cn.hutool.core.convert.Convert.toMap(String.class, String.class, queryMap);
    }

    public static Map<String, List<String>> decodeParams(String paramsStr, String charset) {
        return decodeParams(paramsStr, CharsetUtil.charset(charset));
    }

    public static Map<String, List<String>> decodeParams(String paramsStr, Charset charset) {
        Map<CharSequence, CharSequence> queryMap = UrlQuery.of(paramsStr, charset).getQueryMap();
        if (cn.hutool.core.map.MapUtil.isEmpty(queryMap)) {
            return cn.hutool.core.map.MapUtil.empty();
        } else {
            Map<String, List<String>> params = new LinkedHashMap();
            queryMap.forEach((key, value) -> {
                List<String> values = (List)params.computeIfAbsent(StrUtil.str(key), (k) -> new ArrayList(1));
                values.add(StrUtil.str(value));
            });
            return params;
        }
    }

    public static String urlWithForm(String url, Map<String, Object> form, Charset charset, boolean isEncodeParams) {
        if (isEncodeParams && StrUtil.contains(url, '?')) {
            url = encodeParams(url, charset);
        }

        return urlWithForm(url, toParams(form, charset), charset, false);
    }

    public static String urlWithForm(String url, String queryString, Charset charset, boolean isEncode) {
        if (StrUtil.isBlank(queryString)) {
            if (StrUtil.contains(url, '?')) {
                return isEncode ? encodeParams(url, charset) : url;
            } else {
                return url;
            }
        } else {
            StrBuilder urlBuilder = StrBuilder.create(url.length() + queryString.length() + 16);
            int qmIndex = url.indexOf(63);
            if (qmIndex > 0) {
                urlBuilder.append(isEncode ? encodeParams(url, charset) : url);
                if (!StrUtil.endWith(url, '&')) {
                    urlBuilder.append('&');
                }
            } else {
                urlBuilder.append(url);
                if (qmIndex < 0) {
                    urlBuilder.append('?');
                }
            }

            urlBuilder.append(isEncode ? encodeParams(queryString, charset) : queryString);
            return urlBuilder.toString();
        }
    }

    public static String getCharset(HttpURLConnection conn) {
        return conn == null ? null : getCharset(conn.getContentType());
    }

    public static String getCharset(String contentType) {
        return StrUtil.isBlank(contentType) ? null : ReUtil.get(CHARSET_PATTERN, contentType, 1);
    }

    public static String getString(InputStream in, Charset charset, boolean isGetCharsetFromContent) {
        byte[] contentBytes = IoUtil.readBytes(in);
        return getString(contentBytes, charset, isGetCharsetFromContent);
    }

    public static String getString(byte[] contentBytes, Charset charset, boolean isGetCharsetFromContent) {
        if (null == contentBytes) {
            return null;
        } else {
            if (null == charset) {
                charset = CharsetUtil.CHARSET_UTF_8;
            }

            String content = new String(contentBytes, charset);
            if (isGetCharsetFromContent) {
                String charsetInContentStr = ReUtil.get(META_CHARSET_PATTERN, content, 1);
                if (StrUtil.isNotBlank(charsetInContentStr)) {
                    Charset charsetInContent = null;

                    try {
                        charsetInContent = Charset.forName(charsetInContentStr);
                    } catch (Exception var7) {
                        if (!StrUtil.containsIgnoreCase(charsetInContentStr, "utf-8") && !StrUtil.containsIgnoreCase(charsetInContentStr, "utf8")) {
                            if (StrUtil.containsIgnoreCase(charsetInContentStr, "gbk")) {
                                charsetInContent = CharsetUtil.CHARSET_GBK;
                            }
                        } else {
                            charsetInContent = CharsetUtil.CHARSET_UTF_8;
                        }
                    }

                    if (null != charsetInContent && !charset.equals(charsetInContent)) {
                        content = new String(contentBytes, charsetInContent);
                    }
                }
            }

            return content;
        }
    }

    public static String getMimeType(String filePath, String defaultValue) {
        return (String) ObjectUtil.defaultIfNull(getMimeType(filePath), defaultValue);
    }

    public static String getMimeType(String filePath) {
        return FileUtil.getMimeType(filePath);
    }

    public static String getContentTypeByRequestBody(String body) {
        ContentType contentType = ContentType.get(body);
        return null == contentType ? null : contentType.toString();
    }

    public static SimpleServer createServer(int port) {
        return new SimpleServer(port);
    }

    public static String buildBasicAuth(String username, String password, Charset charset) {
        String data = username.concat(":").concat(password);
        return "Basic " + cn.hutool.core.codec.Base64.encode(data, charset);
    }

    public static void closeCookie() {
        GlobalCookieManager.setCookieManager((CookieManager)null);
    }

    // ========== 新增自定义方法：带Header参数的getH/postH（结尾加H：==========

    /**
     * 新增：带Header的GET请求（基础版）
     * @param urlString 请求地址
     * @param headerMap 请求头集：
     * @return 响应字符：
     */
    public static String getH(String urlString, Map<String, String> headerMap) {
        return getH(urlString, null, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * 新增：带Header+参数的GET请求
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @return 响应字符：
     */
    public static String getH(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap) {
        return getH(urlString, paramMap, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * 新增：带Header+参数+超时的GET请求
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return 响应字符：
     */
    public static String getH(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.get(urlString)
                .timeout(timeout);

        // 设置请求参数
        if (paramMap != null && !paramMap.isEmpty()) {
            request.form(paramMap);
        }

        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }

        HttpResponse response = request.execute();
        int status = response.getStatus();
        String res = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, res);
        }
        
        // 检查是否为JSON格式
        if (res != null && JSONUtil.isTypeJSON(res)) {
            return res;
        } else {
            log.error("get调用接口返回非json,url={},res={}",urlString,res);
            throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "调用接口返回非json");
        }
    }

    /**
     * 新增：带Header的POST请求（表单参数版：
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @param headerMap 请求头集：
     * @return 响应字符：
     */
    public static String postH(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap) {
        return postH(urlString, paramMap, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * 新增：带Header+表单参数+超时的POST请求
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return 响应字符：
     */
    public static String postH(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.post(urlString)
                .timeout(timeout);

        // 设置表单参数
        if (paramMap != null && !paramMap.isEmpty()) {
            request.body(JSONUtil.toJsonStr(paramMap));
        }

        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }

        return request.execute().body();
    }

    /**
     * 新增：带Header的POST请求（JSON体版：
     * @param urlString 请求地址
     * @param body JSON请求：
     * @param headerMap 请求头集：
     * @return 响应字符：
     */
    public static String postH(String urlString, String body, Map<String, String> headerMap) {
        return postH(urlString, body, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * 新增：带Header+JSON：超时的POST请求
     * @param urlString 请求地址
     * @param body JSON请求：
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return 响应字符：
     */
    public static String postH(String urlString, String body, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.post(urlString)
                .timeout(timeout)
                .body(body);
        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }

        HttpResponse response = request.execute();
        int status = response.getStatus();
        String res = response.body();
        
        // 记录响应状态和内容
        if (status != 200) {
            log.warn("HTTP请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, res);
        }
        
        // 检查是否为JSON格式
        if (res != null && JSONUtil.isTypeJSON(res)) {
            return res;
        } else {
            log.error("post调用接口返回非json,url={},res={}",urlString,res);
            throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "调用接口返回非json");
        }
    }

    /**
     * 新增：安全的GET请求，返回HttpResponse对象，允许调用者自行处理响：
     * @param urlString 请求地址
     * @return HttpResponse对象
     */
    public static HttpResponse getSafe(String urlString) {
        return HttpRequest.get(urlString).execute();
    }

    /**
     * 新增：安全的GET请求，带超时时间，返回HttpResponse对象
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @return HttpResponse对象
     */
    public static HttpResponse getSafe(String urlString, int timeout) {
        return HttpRequest.get(urlString).timeout(timeout).execute();
    }

    /**
     * 新增：安全的POST请求，返回HttpResponse对象，允许调用者自行处理响：
     * @param urlString 请求地址
     * @param body 请求：
     * @return HttpResponse对象
     */
    public static HttpResponse postSafe(String urlString, String body) {
        return HttpRequest.post(urlString).body(body).execute();
    }

    /**
     * 新增：安全的POST请求，带超时时间，返回HttpResponse对象
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @return HttpResponse对象
     */
    public static HttpResponse postSafe(String urlString, String body, int timeout) {
        return HttpRequest.post(urlString).body(body).timeout(timeout).execute();
    }

    // ========== 新增：返回HttpResult和JSONObject的方：==========

    /**
     * GET请求，返回HttpResult对象（包含HTTP状态码、响应体、JSON数据等）
     * @param urlString 请求地址
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString) {
        return getForResult(urlString, HttpGlobalConfig.getTimeout());
    }

    /**
     * GET请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString, int timeout) {
        HttpResponse response = HttpRequest.get(urlString).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        String contentType = response.header("Content-Type");
        
        // 记录：00状态码
        if (status != 200) {
            log.warn("HTTP GET请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return new HttpResult(status, body, contentType);
    }

    /**
     * GET请求，返回HttpResult对象（带参数：
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString, Map<String, Object> paramMap) {
        return getForResult(urlString, paramMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * GET请求，返回HttpResult对象（带参数和超时）
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResponse response = HttpRequest.get(urlString).form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP GET请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return new HttpResult(status, body, contentType);
    }

    /**
     * GET请求，返回HttpResult对象（带参数和Header：
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap) {
        return getForResult(urlString, paramMap, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * GET请求，返回HttpResult对象（带参数、Header和超时）
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult getForResult(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.get(urlString)
                .timeout(timeout);
        
        // 设置请求参数
        if (paramMap != null && !paramMap.isEmpty()) {
            request.form(paramMap);
        }
        
        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }
        
        HttpResponse response = request.execute();
        int status = response.getStatus();
        String body = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP GET请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return new HttpResult(status, body, contentType);
    }

    /**
     * POST请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param body 请求：
     * @return HttpResult对象
     */
    public static HttpResult postForResult(String urlString, String body) {
        return postForResult(urlString, body, HttpGlobalConfig.getTimeout());
    }

    /**
     * POST请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult postForResult(String urlString, String body, int timeout) {
        HttpResponse response = HttpRequest.post(urlString).timeout(timeout).body(body).execute();
        int status = response.getStatus();
        String responseBody = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP POST请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return new HttpResult(status, responseBody, contentType);
    }

    /**
     * POST请求，返回HttpResult对象（带Header：
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @return HttpResult对象
     */
    public static HttpResult postForResult(String urlString, String body, Map<String, String> headerMap) {
        return postForResult(urlString, body, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * POST请求，返回HttpResult对象（带Header和超时）
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult postForResult(String urlString, String body, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.post(urlString)
                .timeout(timeout)
                .body(body);
        
        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }
        
        HttpResponse response = request.execute();
        int status = response.getStatus();
        String responseBody = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP POST请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return new HttpResult(status, responseBody, contentType);
    }

    /**
     * POST请求（表单），返回HttpResult对象
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @return HttpResult对象
     */
    public static HttpResult postFormForResult(String urlString, Map<String, Object> paramMap) {
        return postFormForResult(urlString, paramMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * POST请求（表单），返回HttpResult对象
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult postFormForResult(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResponse response = HttpRequest.post(urlString).form(paramMap).timeout(timeout).execute();
        int status = response.getStatus();
        String responseBody = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP POST请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return new HttpResult(status, responseBody, contentType);
    }

    /**
     * PUT请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param body 请求：
     * @return HttpResult对象
     */
    public static HttpResult putForResult(String urlString, String body) {
        return putForResult(urlString, body, HttpGlobalConfig.getTimeout());
    }

    /**
     * PUT请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult putForResult(String urlString, String body, int timeout) {
        HttpResponse response = HttpRequest.put(urlString).timeout(timeout).body(body).execute();
        int status = response.getStatus();
        String responseBody = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP PUT请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return new HttpResult(status, responseBody, contentType);
    }

    /**
     * PUT请求，返回HttpResult对象（带Header：
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @return HttpResult对象
     */
    public static HttpResult putForResult(String urlString, String body, Map<String, String> headerMap) {
        return putForResult(urlString, body, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * PUT请求，返回HttpResult对象（带Header和超时）
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult putForResult(String urlString, String body, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.put(urlString)
                .timeout(timeout)
                .body(body);
        
        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }
        
        HttpResponse response = request.execute();
        int status = response.getStatus();
        String responseBody = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP PUT请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, responseBody);
        }
        
        return new HttpResult(status, responseBody, contentType);
    }

    /**
     * DELETE请求，返回HttpResult对象
     * @param urlString 请求地址
     * @return HttpResult对象
     */
    public static HttpResult deleteForResult(String urlString) {
        return deleteForResult(urlString, HttpGlobalConfig.getTimeout());
    }

    /**
     * DELETE请求，返回HttpResult对象
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult deleteForResult(String urlString, int timeout) {
        HttpResponse response = HttpRequest.delete(urlString).timeout(timeout).execute();
        int status = response.getStatus();
        String body = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP DELETE请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return new HttpResult(status, body, contentType);
    }

    /**
     * DELETE请求，返回HttpResult对象（带Header：
     * @param urlString 请求地址
     * @param headerMap 请求头集：
     * @return HttpResult对象
     */
    public static HttpResult deleteForResult(String urlString, Map<String, String> headerMap) {
        return deleteForResult(urlString, headerMap, HttpGlobalConfig.getTimeout());
    }

    /**
     * DELETE请求，返回HttpResult对象（带Header和超时）
     * @param urlString 请求地址
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return HttpResult对象
     */
    public static HttpResult deleteForResult(String urlString, Map<String, String> headerMap, int timeout) {
        HttpRequest request = HttpRequest.delete(urlString)
                .timeout(timeout);
        
        // 设置请求：
        if (headerMap != null && !headerMap.isEmpty()) {
            request.addHeaders(headerMap);
        }
        
        HttpResponse response = request.execute();
        int status = response.getStatus();
        String body = response.body();
        String contentType = response.header("Content-Type");
        
        if (status != 200) {
            log.warn("HTTP DELETE请求返回：00状态码: {}, URL: {}, 响应: {}", status, urlString, body);
        }
        
        return new HttpResult(status, body, contentType);
    }

    // ========== 新增：直接返回JSONObject的方：==========

    /**
     * GET请求，直接返回JSONObject（如果响应不是JSON或请求失败，抛出异常：
     * @param urlString 请求地址
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString) {
        return getForJson(urlString, DEFAULT_GET_TIMEOUT);
    }

    /**
     * GET请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString, int timeout) {
        HttpResult result = getForResult(urlString, timeout);
        
        if (!result.isSuccess()) {
            log.error("GET请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "GET请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("GET请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * GET请求，直接返回JSONObject（带参数：
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString, Map<String, Object> paramMap) {
        return getForJson(urlString, paramMap, DEFAULT_GET_TIMEOUT);
    }

    /**
     * GET请求，直接返回JSONObject（带参数和超时）
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResult result = getForResult(urlString, paramMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("GET请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "GET请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("GET请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * GET请求，直接返回JSONObject（带参数和Header：
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap) {
        return getForJson(urlString, paramMap, headerMap, DEFAULT_GET_TIMEOUT);
    }

    /**
     * GET请求，直接返回JSONObject（带参数、Header和超时）
     * @param urlString 请求地址
     * @param paramMap 请求参数
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJson(String urlString, Map<String, Object> paramMap, Map<String, String> headerMap, int timeout) {
        HttpResult result = getForResult(urlString, paramMap, headerMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("GET请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "GET请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("GET请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * POST请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJson(String urlString, String body) {
        return postForJson(urlString, body, DEFAULT_POST_TIMEOUT);
    }

    /**
     * POST请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJson(String urlString, String body, int timeout) {
        HttpResult result = postForResult(urlString, body, timeout);
        
        if (!result.isSuccess()) {
            log.error("POST请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "POST请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("POST请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * POST请求，直接返回JSONObject（带Header：
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJson(String urlString, String body, Map<String, String> headerMap) {
        return postForJson(urlString, body, headerMap, DEFAULT_POST_TIMEOUT);
    }

    /**
     * POST请求，直接返回JSONObject（带Header和超时）
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJson(String urlString, String body, Map<String, String> headerMap, int timeout) {
        HttpResult result = postForResult(urlString, body, headerMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("POST请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "POST请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("POST请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * POST请求（表单），直接返回JSONObject
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postFormForJson(String urlString, Map<String, Object> paramMap) {
        return postFormForJson(urlString, paramMap, DEFAULT_POST_TIMEOUT);
    }

    /**
     * POST请求（表单），直接返回JSONObject
     * @param urlString 请求地址
     * @param paramMap 表单参数
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postFormForJson(String urlString, Map<String, Object> paramMap, int timeout) {
        HttpResult result = postFormForResult(urlString, paramMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("POST请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "POST请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("POST请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * PUT请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject putForJson(String urlString, String body) {
        return putForJson(urlString, body, DEFAULT_PUT_TIMEOUT);
    }

    /**
     * PUT请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject putForJson(String urlString, String body, int timeout) {
        HttpResult result = putForResult(urlString, body, timeout);
        
        if (!result.isSuccess()) {
            log.error("PUT请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "PUT请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("PUT请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * PUT请求，直接返回JSONObject（带Header：
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject putForJson(String urlString, String body, Map<String, String> headerMap) {
        return putForJson(urlString, body, headerMap, DEFAULT_PUT_TIMEOUT);
    }

    /**
     * PUT请求，直接返回JSONObject（带Header和超时）
     * @param urlString 请求地址
     * @param body 请求：
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject putForJson(String urlString, String body, Map<String, String> headerMap, int timeout) {
        HttpResult result = putForResult(urlString, body, headerMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("PUT请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "PUT请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("PUT请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * DELETE请求，直接返回JSONObject
     * @param urlString 请求地址
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject deleteForJson(String urlString) {
        return deleteForJson(urlString, DEFAULT_DELETE_TIMEOUT);
    }

    /**
     * DELETE请求，直接返回JSONObject
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject deleteForJson(String urlString, int timeout) {
        HttpResult result = deleteForResult(urlString, timeout);
        
        if (!result.isSuccess()) {
            log.error("DELETE请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "DELETE请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("DELETE请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    /**
     * DELETE请求，直接返回JSONObject（带Header：
     * @param urlString 请求地址
     * @param headerMap 请求头集：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject deleteForJson(String urlString, Map<String, String> headerMap) {
        return deleteForJson(urlString, headerMap, DEFAULT_DELETE_TIMEOUT);
    }

    /**
     * DELETE请求，直接返回JSONObject（带Header和超时）
     * @param urlString 请求地址
     * @param headerMap 请求头集：
     * @param timeout 超时时间（毫秒）
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject deleteForJson(String urlString, Map<String, String> headerMap, int timeout) {
        HttpResult result = deleteForResult(urlString, headerMap, timeout);
        
        if (!result.isSuccess()) {
            log.error("DELETE请求失败，URL: {}, HTTP状态码: {}, 错误信息: {}", urlString, result.getHttpCode(), result.getErrorMessage());
            FrameworkErrorCode errorCode = FrameworkErrorCode.getByHttpCode(result.getHttpCode());
            throw new BusinessException(errorCode != null ? errorCode.getCode() : 900000 + result.getHttpCode(), 
                    errorCode != null ? errorCode.getMsg() : "DELETE请求失败");
        }
        
        if (result.getJsonData() == null) {
            log.error("DELETE请求响应不是JSON格式，URL: {}, 响应: {}", urlString, result.getBody());
            throw new BusinessException(FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getCode(), 
                    FrameworkErrorCode.HTTP_RESPONSE_NOT_JSON.getMsg() + "：" + result.getBody());
        }
        
        return result.getJsonData();
    }

    // ========== 新增：泛型反序列化支：==========

    /**
     * GET请求，直接返回指定类型的对象
     * @param urlString 请求地址
     * @param clazz 目标类型
     * @return 目标对象，失败时抛出BusinessException
     */
    public static <T> T getForObject(String urlString, Class<T> clazz) {
        return getForObject(urlString, clazz, DEFAULT_GET_TIMEOUT);
    }

    /**
     * GET请求，直接返回指定类型的对象
     * @param urlString 请求地址
     * @param clazz 目标类型
     * @param timeout 超时时间（毫秒）
     * @return 目标对象，失败时抛出BusinessException
     */
    public static <T> T getForObject(String urlString, Class<T> clazz, int timeout) {
        JSONObject json = getForJson(urlString, timeout);
        return JSONUtil.toBean(json, clazz);
    }

    /**
     * POST请求，直接返回指定类型的对象
     * @param urlString 请求地址
     * @param body 请求：
     * @param clazz 目标类型
     * @return 目标对象，失败时抛出BusinessException
     */
    public static <T> T postForObject(String urlString, String body, Class<T> clazz) {
        return postForObject(urlString, body, clazz, DEFAULT_POST_TIMEOUT);
    }

    /**
     * POST请求，直接返回指定类型的对象
     * @param urlString 请求地址
     * @param body 请求：
     * @param clazz 目标类型
     * @param timeout 超时时间（毫秒）
     * @return 目标对象，失败时抛出BusinessException
     */
    public static <T> T postForObject(String urlString, String body, Class<T> clazz, int timeout) {
        JSONObject json = postForJson(urlString, body, timeout);
        return JSONUtil.toBean(json, clazz);
    }

    // ========== 新增：重试机制支：==========

    /**
     * GET请求，带重试机制，返回JSONObject
     * @param urlString 请求地址
     * @param maxRetries 最大重试次：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJsonWithRetry(String urlString, int maxRetries) {
        return getForJsonWithRetry(urlString, DEFAULT_GET_TIMEOUT, maxRetries);
    }

    /**
     * GET请求，带重试机制，返回JSONObject
     * @param urlString 请求地址
     * @param timeout 超时时间（毫秒）
     * @param maxRetries 最大重试次：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject getForJsonWithRetry(String urlString, int timeout, int maxRetries) {
        BusinessException lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                return getForJson(urlString, timeout);
            } catch (BusinessException e) {
                lastException = e;
                if (i < maxRetries - 1) {
                    log.warn("第{}次请求失败，准备重试: URL={}, 错误={}", i + 1, urlString, e.getMessage());
                    try {
                        // 指数退避：：次等1秒，：次等2秒，：次等4：..
                        Thread.sleep(1000L * (long) Math.pow(2, i));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "重试被中断");
                    }
                }
            }
        }
        
        throw lastException != null ? lastException : new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "请求失败，已重试" + maxRetries + "次");
    }

    /**
     * POST请求，带重试机制，返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @param maxRetries 最大重试次：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJsonWithRetry(String urlString, String body, int maxRetries) {
        return postForJsonWithRetry(urlString, body, DEFAULT_POST_TIMEOUT, maxRetries);
    }

    /**
     * POST请求，带重试机制，返回JSONObject
     * @param urlString 请求地址
     * @param body 请求：
     * @param timeout 超时时间（毫秒）
     * @param maxRetries 最大重试次：
     * @return JSONObject对象，失败时抛出BusinessException
     */
    public static JSONObject postForJsonWithRetry(String urlString, String body, int timeout, int maxRetries) {
        BusinessException lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                return postForJson(urlString, body, timeout);
            } catch (BusinessException e) {
                lastException = e;
                if (i < maxRetries - 1) {
                    log.warn("第{}次请求失败，准备重试: URL={}, 错误={}", i + 1, urlString, e.getMessage());
                    try {
                        Thread.sleep(1000L * (long) Math.pow(2, i));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "重试被中断");
                    }
                }
            }
        }
        
        throw lastException != null ? lastException : new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "请求失败，已重试" + maxRetries + "次");
    }

}
