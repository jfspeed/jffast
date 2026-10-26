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

import cn.jfspeed.common.security.util.ApiParamSignUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class ApiParamSignUtilTest {

    @Nested
    @DisplayName("generateAndAddApiSecret")
    class GenerateAndAddApiSecret {

        @Test
        @DisplayName("应生成非空签名")
        void shouldGenerateNonNullSignature() {
            String secretKey = "testSecretKey";
            Map<String, String> params = new HashMap<>();
            params.put("productCode", "12345");
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));

            Map<String, String> signed = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params);

            assertNotNull(signed);
            assertNotNull(signed.get("sign"));
            assertFalse(signed.get("sign").isEmpty());
        }

        @Test
        @DisplayName("签名长度应为 64（HMAC-SHA256）")
        void shouldGenerateSha256Signature() {
            String secretKey = "testSecretKey";
            Map<String, String> params = new HashMap<>();
            params.put("productCode", "12345");

            Map<String, String> signed = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params);

            assertEquals(64, signed.get("sign").length());
        }

        @Test
        @DisplayName("相同参数应生成相同签名")
        void shouldGenerateDeterministicSignature() {
            String secretKey = "testSecretKey";
            Map<String, String> params1 = new HashMap<>();
            params1.put("productCode", "12345");
            params1.put("timestamp", "1000000");

            Map<String, String> params2 = new HashMap<>();
            params2.put("productCode", "12345");
            params2.put("timestamp", "1000000");

            Map<String, String> signed1 = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params1);
            Map<String, String> signed2 = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params2);

            assertEquals(signed1.get("sign"), signed2.get("sign"));
        }

        @Test
        @DisplayName("不同参数应生成不同签名")
        void shouldGenerateDifferentSignatures() {
            String secretKey = "testSecretKey";
            Map<String, String> params1 = new HashMap<>();
            params1.put("productCode", "12345");

            Map<String, String> params2 = new HashMap<>();
            params2.put("productCode", "67890");

            Map<String, String> signed1 = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params1);
            Map<String, String> signed2 = ApiParamSignUtil.generateAndAddApiSecret(secretKey, params2);

            assertNotEquals(signed1.get("sign"), signed2.get("sign"));
        }

        @Test
        @DisplayName("null 参数应抛出异常")
        void shouldThrowOnNullParams() {
            assertThrows(IllegalArgumentException.class,
                    () -> ApiParamSignUtil.generateAndAddApiSecret("key", null));
        }

        @Test
        @DisplayName("空参数应抛出异常")
        void shouldThrowOnEmptyParams() {
            assertThrows(IllegalArgumentException.class,
                    () -> ApiParamSignUtil.generateAndAddApiSecret("key", new HashMap<>()));
        }
    }

    @Nested
    @DisplayName("appendParamsToUrl")
    class AppendParamsToUrl {

        @Test
        @DisplayName("应正确拼接参数到 URL")
        void shouldAppendParamsToUrl() throws Exception {
            String baseUrl = "https://example.com/api";
            Map<String, String> params = new HashMap<>();
            params.put("key1", "value1");
            params.put("key2", "value2");

            String result = ApiParamSignUtil.appendParamsToUrl(baseUrl, params);

            assertTrue(result.startsWith(baseUrl + "?"));
            assertTrue(result.contains("key1=value1"));
            assertTrue(result.contains("key2=value2"));
        }

        @Test
        @DisplayName("空参数应返回原始 URL")
        void shouldReturnOriginalUrlOnEmptyParams() throws Exception {
            String baseUrl = "https://example.com/api";

            String result = ApiParamSignUtil.appendParamsToUrl(baseUrl, new HashMap<>());

            assertEquals(baseUrl, result);
        }

        @Test
        @DisplayName("null 参数应返回原始 URL")
        void shouldReturnOriginalUrlOnNullParams() throws Exception {
            String baseUrl = "https://example.com/api";

            String result = ApiParamSignUtil.appendParamsToUrl(baseUrl, null);

            assertEquals(baseUrl, result);
        }

        @Test
        @DisplayName("null 或空 URL 应抛出异常")
        void shouldThrowOnInvalidBaseUrl() {
            Map<String, String> params = new HashMap<>();
            params.put("key", "value");

            assertThrows(IllegalArgumentException.class,
                    () -> ApiParamSignUtil.appendParamsToUrl(null, params));
            assertThrows(IllegalArgumentException.class,
                    () -> ApiParamSignUtil.appendParamsToUrl("  ", params));
        }

        @Test
        @DisplayName("应正确处理已有参数的 URL")
        void shouldHandleExistingQueryParams() throws Exception {
            String baseUrl = "https://example.com/api?existing=param";
            Map<String, String> params = new HashMap<>();
            params.put("key", "value");

            String result = ApiParamSignUtil.appendParamsToUrl(baseUrl, params);

            assertTrue(result.contains("existing=param"));
            assertTrue(result.contains("key=value"));
            assertTrue(result.contains("&"));
        }
    }
}
