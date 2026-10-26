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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class SignUtils {

    private static final long DEFAULT_TIMESTAMP_TIMEOUT = 5 * 60 * 1000L;

    public static Map<String, String> generateSign(Map<String, String> params, String secretKey) {
        if (params == null) {
            params = new HashMap<>();
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("签名密钥secretKey不能为空");
        }

        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        params.remove("sign");

        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder signStr = new StringBuilder();
        for (String key : sortedKeys) {
            String value = params.get(key);
            if (value != null && !value.trim().isEmpty()) {
                signStr.append(key).append(value);
            }
        }

        signStr.append(secretKey);
        String sign = DigestUtils.sha256Hex(signStr.toString());

        params.put("sign", sign);
        return params;
    }

    private static String generateSignVerify(Map<String, String> params, String secretKey) {
        if (params == null) {
            params = new HashMap<>();
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("签名密钥secretKey不能为空");
        }

        params.remove("sign");

        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder signStr = new StringBuilder();
        for (String key : sortedKeys) {
            String value = params.get(key);
            if (value != null && !value.trim().isEmpty()) {
                signStr.append(key).append(value);
            }
        }

        signStr.append(secretKey);
        String sign = DigestUtils.sha256Hex(signStr.toString());

        return sign;
    }

    public static boolean verifySign(Map<String, String> requestParams, String secretKey) {
        try {
            if (requestParams == null || secretKey == null || secretKey.trim().isEmpty()) {
                return false;
            }

            String requestSign = requestParams.remove("sign");
            if (requestSign == null || requestSign.trim().isEmpty()) {
                return false;
            }

            String timestampStr = requestParams.get("timestamp");
            if (timestampStr == null || timestampStr.trim().isEmpty()) {
                return false;
            }
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            if (Math.abs(now - timestamp) > DEFAULT_TIMESTAMP_TIMEOUT) {
                return false;
            }

            Map<String, String> serverParams = new HashMap<>(requestParams);
            String serverSign = generateSignVerify(serverParams, secretKey);

            return requestSign.equalsIgnoreCase(serverSign);

        } catch (Exception e) {
            return false;
        }
    }
}
