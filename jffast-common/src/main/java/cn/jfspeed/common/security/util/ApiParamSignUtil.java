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
package cn.jfspeed.common.security.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class ApiParamSignUtil {

    public static final String apiParamSignKey = "sign";

    public static Map<String, String> generateAndAddApiSecret(String secretKey, Map<String, String> urlParams) {
        if (urlParams == null || urlParams.isEmpty()) {
            throw new IllegalArgumentException("参数缺失");
        }
        Map<String, String> newParams = new HashMap<>(urlParams);
        String secret = generateApiSecret(secretKey, urlParams);
        newParams.put(apiParamSignKey, secret);
        return newParams;
    }

    private static String generateApiSecret(String secretKey, Map<String, String> urlParams) {
        if (urlParams == null || urlParams.isEmpty()) {
            throw new IllegalArgumentException("参数缺失");
        }
        urlParams.remove(apiParamSignKey);
        TreeMap<String, String> sortedParams = new TreeMap<>(urlParams);
        StringBuilder paramStr = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            paramStr.append(entry.getKey())
                    .append("=")
                    .append(entry.getValue())
                    .append("&");
        }
        paramStr.append("secretKey=").append(secretKey);
        return hmacSha256(paramStr.toString(), secretKey);
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256签名失败", e);
        }
    }

    public static String appendParamsToUrl(String baseUrl, Map<String, String> params) throws UnsupportedEncodingException {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("基础URL不能为空");
        }
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        boolean hasExistingParams = baseUrl.contains("?");
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.trim().isEmpty() || value == null) {
                continue;
            }
            String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
            String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            if (hasExistingParams) {
                urlBuilder.append("&");
            } else {
                urlBuilder.append("?");
                hasExistingParams = true;
            }
            urlBuilder.append(encodedKey).append("=").append(encodedValue);
        }
        return urlBuilder.toString();
    }

    public static String sendPostWithJsonNode(String fullUrl, String jsonNode) throws IOException {
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded,application/json");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonNode.getBytes(StandardCharsets.UTF_8));
        }
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode == 200)
                ? conn.getInputStream()
                : conn.getErrorStream();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }


}
