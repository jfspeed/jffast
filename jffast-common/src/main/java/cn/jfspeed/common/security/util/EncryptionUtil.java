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

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author jffast
 */
@Slf4j
public class EncryptionUtil {
    public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA1";
    public static final int SALT_BYTE_SIZE = 16;
    public static final int HASH_BIT_SIZE = 64;
    public static final int PBKDF2_ITERATIONS = 600000;
    public static final String API_PARAM_SIGN_KEY = "sign";
    public static final String API_PARAM_TIME_KEY = "timestamp";

    private EncryptionUtil() {
    }

    public static String getEncryptPassword(String rawPassword) {
        String sha256Pwd = DigestUtil.sha256Hex(rawPassword);
        String newPassword = BCrypt.hashpw(sha256Pwd);
        return newPassword;
    }

    public static boolean verifyPassword(String inputPassword, String dbPassword) {
        String inputSha256Pwd = DigestUtil.sha256Hex(inputPassword);
        return BCrypt.checkpw(inputSha256Pwd, dbPassword);
    }

    public static String generateRandom(int length, boolean isNumber) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        if (isNumber) {
            str = "0123456789";
        }

        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; ++i) {
            int number = secureRandom.nextInt(str.length());
            sb.append(str.charAt(number));
        }

        return sb.toString();
    }

    public static Map<String, String> generateAndAddApiSecret(String secretKey, Map<String, String> urlParams) {
        if (urlParams == null || urlParams.isEmpty()) {
            throw new IllegalArgumentException("参数缺失");
        }
        Map<String, String> newParams = new HashMap<>(urlParams);
        String secret = generateApiSecret(secretKey, urlParams);
        newParams.put(API_PARAM_SIGN_KEY, secret);
        return newParams;
    }

    private static String generateApiSecret(String secretKey, Map<String, String> urlParams) {
        if (urlParams == null || urlParams.isEmpty()) {
            throw new IllegalArgumentException("参数缺失");
        }

        urlParams.remove(API_PARAM_SIGN_KEY);
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

    public static void verifyFromApiParams(String secretKey, Map<String, String> urlParams) {
        String receivedSecret = urlParams.get(API_PARAM_SIGN_KEY);
        if (receivedSecret == null || receivedSecret.trim().isEmpty()) {
            throw new BusinessException("签名参数缺失");
        }

        try {
            String expectedSecret = generateApiSecret(secretKey, urlParams);

            if (!expectedSecret.equalsIgnoreCase(receivedSecret.trim())) {
                log.warn("签名验证不通过");
                throw new BusinessException("签名验证不通过");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException("参数不完整时验证失败");
        }
    }

    public static void verifyFromApiParamsWithTimeout(String secretKey,
                                                      Map<String, String> urlParams,
                                                      long timeoutMillis) {
        verifyFromApiParams(secretKey, urlParams);

        try {
            String timestampStr = urlParams.get(API_PARAM_TIME_KEY);
            long requestTime = Long.parseLong(timestampStr);
            long currentTime = System.currentTimeMillis();

            if (Math.abs(currentTime - requestTime) > timeoutMillis) {
                throw new BusinessException("签名已过期");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("时间戳参数不正确");
        }
    }
}
