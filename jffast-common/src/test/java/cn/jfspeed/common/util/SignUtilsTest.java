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
package cn.jfspeed.common.util;

import cn.jfspeed.common.security.util.SignUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class SignUtilsTest {

    private static final String SECRET_KEY = "test_secret_key_2024";

    @Nested
    @DisplayName("generateSign")
    class GenerateSign {

        @Test
        @DisplayName("应生成包含 sign 和 timestamp 的参数")
        void shouldGenerateSignWithTimestamp() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");

            Map<String, String> result = SignUtils.generateSign(params, SECRET_KEY);

            assertNotNull(result.get("sign"));
            assertNotNull(result.get("timestamp"));
            assertFalse(result.get("sign").isEmpty());
            assertTrue(result.get("sign").length() == 64); // SHA256 hex = 64 chars
        }

        @Test
        @DisplayName("null params 应正常工作")
        void shouldHandleNullParams() {
            Map<String, String> result = SignUtils.generateSign(null, SECRET_KEY);
            assertNotNull(result.get("sign"));
        }

        @Test
        @DisplayName("null secretKey 应抛 IllegalArgumentException")
        void shouldThrowForNullSecretKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    SignUtils.generateSign(new HashMap<>(), null));
        }

        @Test
        @DisplayName("空 secretKey 应抛 IllegalArgumentException")
        void shouldThrowForEmptySecretKey() {
            assertThrows(IllegalArgumentException.class, () ->
                    SignUtils.generateSign(new HashMap<>(), "  "));
        }
    }

    @Nested
    @DisplayName("verifySign")
    class VerifySign {

        @Test
        @DisplayName("合法签名应验证通过")
        void shouldVerifyValidSign() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");
            params.put("businessModule", "order");

            Map<String, String> signed = SignUtils.generateSign(params, SECRET_KEY);
            assertTrue(SignUtils.verifySign(signed, SECRET_KEY));
        }

        @Test
        @DisplayName("篡改参数后应验证失败")
        void shouldFailWhenTampered() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");

            Map<String, String> signed = SignUtils.generateSign(params, SECRET_KEY);
            signed.put("userId", "9999"); // 篡改

            assertFalse(SignUtils.verifySign(signed, SECRET_KEY));
        }

        @Test
        @DisplayName("错误密钥应验证失败")
        void shouldFailWithWrongKey() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");

            Map<String, String> signed = SignUtils.generateSign(params, SECRET_KEY);
            assertFalse(SignUtils.verifySign(signed, "wrong_key"));
        }

        @Test
        @DisplayName("缺少 sign 应返回 false")
        void shouldFailWithoutSign() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));

            assertFalse(SignUtils.verifySign(params, SECRET_KEY));
        }

        @Test
        @DisplayName("缺少 timestamp 应返回 false")
        void shouldFailWithoutTimestamp() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");
            params.put("sign", "dummy");

            assertFalse(SignUtils.verifySign(params, SECRET_KEY));
        }

        @Test
        @DisplayName("null 参数应返回 false")
        void shouldFailForNullParams() {
            assertFalse(SignUtils.verifySign(null, SECRET_KEY));
        }

        @Test
        @DisplayName("过期 timestamp 应返回 false")
        void shouldFailForExpiredTimestamp() {
            Map<String, String> params = new HashMap<>();
            params.put("userId", "1001");

            Map<String, String> signed = SignUtils.generateSign(params, SECRET_KEY);
            // 设置一个 10 分钟前的时间戳（超过 5 分钟超时）
            long expiredTimestamp = System.currentTimeMillis() - 10 * 60 * 1000;
            signed.put("timestamp", String.valueOf(expiredTimestamp));

            assertFalse(SignUtils.verifySign(signed, SECRET_KEY));
        }
    }
}
