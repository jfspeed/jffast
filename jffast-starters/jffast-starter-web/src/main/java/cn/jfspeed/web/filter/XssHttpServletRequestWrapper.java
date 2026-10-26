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

import cn.jfspeed.web.util.XssUtil;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * XSS请求包装类，用于过滤请求参数与 JSON 请求体中的XSS攻击代码。
 *
 * <ul>
 *   <li>form/query 参数、请求头（{@code getHeader} / {@code getHeaders}）：直接清洗取值（见 {@link XssUtil#clean}）；</li>
 *   <li>JSON 请求体（Content-Type 含 {@code json}）：解析后递归清洗所有字符串值，
 *       {@code jsonSkipFields} 豁免的属性（如携带代码/模板的 bpmnXml、contentTemplate）跳过清洗。</li>
 * </ul>
 *
 * @author jffast
 */
public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private static final String JSON_CONTENT_TYPE = "json";

    /** 清洗后的 JSON 请求体；null 表示不做 body 清洗（非 JSON / 空 body / 未启用） */
    private final byte[] cleanedJsonBody;

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        this(request, true, Collections.emptySet());
    }

    /**
     * @param request        原始请求
     * @param cleanJsonBody  是否清洗 JSON 请求体
     * @param jsonSkipFields JSON 清洗豁免的属性名集合
     */
    public XssHttpServletRequestWrapper(HttpServletRequest request, boolean cleanJsonBody, Set<String> jsonSkipFields) {
        super(request);
        this.cleanedJsonBody = cleanJsonBody ? readAndCleanJsonBody(request, jsonSkipFields) : null;
    }

    private byte[] readAndCleanJsonBody(HttpServletRequest request, Set<String> jsonSkipFields) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains(JSON_CONTENT_TYPE)) {
            return null;
        }
        try (ServletInputStream in = request.getInputStream()) {
            byte[] raw = in.readAllBytes();
            if (raw.length == 0) {
                return null;
            }
            Charset charset = StandardCharsets.UTF_8;
            String encoding = request.getCharacterEncoding();
            if (encoding != null && !encoding.isBlank()) {
                charset = Charset.forName(encoding);
            }
            String cleaned = XssUtil.cleanJson(new String(raw, charset), jsonSkipFields);
            return cleaned.getBytes(charset);
        } catch (IOException e) {
            throw new UncheckedIOException("读取 JSON 请求体失败", e);
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (cleanedJsonBody == null) {
            return super.getInputStream();
        }
        ByteArrayInputStream in = new ByteArrayInputStream(cleanedJsonBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return in.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException("异步读取未启用");
            }

            @Override
            public int read() {
                return in.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (cleanedJsonBody == null) {
            return super.getReader();
        }
        String encoding = getCharacterEncoding();
        Charset charset = encoding != null && !encoding.isBlank()
                ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(cleanedJsonBody), charset));
    }

    @Override
    public int getContentLength() {
        return cleanedJsonBody != null ? cleanedJsonBody.length : super.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return cleanedJsonBody != null ? cleanedJsonBody.length : super.getContentLengthLong();
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return XssUtil.clean(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) {
            return null;
        }
        String[] cleanedValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleanedValues[i] = XssUtil.clean(values[i]);
        }
        return cleanedValues;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> map = super.getParameterMap();
        Map<String, String[]> cleanedMap = new HashMap<>();
        for (Map.Entry<String, String[]> entry : map.entrySet()) {
            String[] values = entry.getValue();
            String[] cleanedValues = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                cleanedValues[i] = XssUtil.clean(values[i]);
            }
            cleanedMap.put(entry.getKey(), cleanedValues);
        }
        return cleanedMap;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return XssUtil.clean(value);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        Enumeration<String> values = super.getHeaders(name);
        if (values == null) {
            return null;
        }
        List<String> cleanedValues = new ArrayList<>();
        while (values.hasMoreElements()) {
            cleanedValues.add(XssUtil.clean(values.nextElement()));
        }
        return Collections.enumeration(cleanedValues);
    }
}
