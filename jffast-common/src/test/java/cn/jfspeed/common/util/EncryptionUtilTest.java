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

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class EncryptionUtilTest {

    @Nested
    @DisplayName("密码加密与验证")
    class Password {

        @Test
        @DisplayName("加密后的密码应能通过验证")
        void shouldVerifyEncryptedPassword() {
            String raw = "A@123456";
            String encrypted = EncryptionUtil.getEncryptPassword(raw);

            assertNotNull(encrypted);
            assertTrue(EncryptionUtil.verifyPassword(raw, encrypted));
        }

        @Test
        @DisplayName("错误密码不应通过验证")
        void shouldRejectWrongPassword() {
            String encrypted = EncryptionUtil.getEncryptPassword("A@123456");
            assertFalse(EncryptionUtil.verifyPassword("wrong", encrypted));
        }

        @Test
        @DisplayName("新生成的哈希必须使用 >=12 的 BCrypt 强度因子（OWASP 当前建议下限）")
        void shouldUseStrongBcryptCost() {
            String encrypted = EncryptionUtil.getEncryptPassword("A@123456");
            // BCrypt 哈希形如 $2a$12$<22位salt><31位hash>，第 4~5 个字符即 log rounds
            assertTrue(encrypted.matches("^\\$2[abxy]\\$(\\d{2})\\$.+$"),
                    "不是标准的 BCrypt 哈希格式：" + encrypted);
            int cost = Integer.parseInt(encrypted.substring(4, 6));
            assertTrue(cost >= 12,
                    "BCrypt 强度因子过低（" + cost + "），OWASP 当前建议 >= 12");
        }

        @Test
        @DisplayName("历史低强度因子（cost=10）的存量哈希仍能正常校验，升级成本不应强制改密")
        void shouldStillVerifyLegacyCostTenHash() {
            // 固定 salt 造一个 cost=10 的哈希，模拟存量库里的老口令
            String legacy = BCrypt.hashpw(DigestUtil.sha256Hex("A@123456"), BCrypt.gensalt(10));
            assertTrue(EncryptionUtil.verifyPassword("A@123456", legacy));
            assertFalse(EncryptionUtil.verifyPassword("wrong", legacy));
        }
    }

    @Nested
    @DisplayName("generateRandom")
    class GenerateRandom {

        @Test
        @DisplayName("应生成指定长度的随机字符串")
        void shouldGenerateCorrectLength() {
            String random = EncryptionUtil.generateRandom(10, false);
            assertEquals(10, random.length());
        }

        @Test
        @DisplayName("纯数字模式应只包含数字")
        void shouldGenerateOnlyNumbers() {
            String random = EncryptionUtil.generateRandom(20, true);
            assertTrue(random.matches("\\d+"));
        }
    }

    @Nested
    @DisplayName("API 签名")
    class ApiSignature {

        @Test
        @DisplayName("生成的签名应能通过验证")
        void shouldVerifyGeneratedSignature() {
            String secretKey = "testKey";
            Map<String, String> params = new HashMap<>();
            params.put("productCode", "test");
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));

            Map<String, String> signed = EncryptionUtil.generateAndAddApiSecret(secretKey, params);
            assertNotNull(signed.get("sign"));

            assertDoesNotThrow(() -> EncryptionUtil.verifyFromApiParams(secretKey, signed));
        }

        @Test
        @DisplayName("篡改参数后签名验证应失败")
        void shouldFailOnTamperedParams() {
            String secretKey = "testKey";
            Map<String, String> params = new HashMap<>();
            params.put("productCode", "test");
            params.put("timestamp", String.valueOf(System.currentTimeMillis()));

            Map<String, String> signed = EncryptionUtil.generateAndAddApiSecret(secretKey, params);
            signed.put("productCode", "tampered");

            assertThrows(BusinessException.class,
                    () -> EncryptionUtil.verifyFromApiParams(secretKey, signed));
        }

        @Test
        @DisplayName("签名应为 64 位十六进制字符串（HMAC-SHA256）")
        void shouldGenerateSha256Signature() {
            String secretKey = "testKey";
            Map<String, String> params = new HashMap<>();
            params.put("productCode", "test");

            Map<String, String> signed = EncryptionUtil.generateAndAddApiSecret(secretKey, params);
            String sign = signed.get("sign");

            assertNotNull(sign);
            assertEquals(64, sign.length());
            assertTrue(sign.matches("[0-9a-f]+"));
        }

        @Test
        @DisplayName("空参数应抛出异常")
        void shouldThrowOnEmptyParams() {
            assertThrows(IllegalArgumentException.class,
                    () -> EncryptionUtil.generateAndAddApiSecret("key", new HashMap<>()));
        }

        @Test
        @DisplayName("null 参数应抛出异常")
        void shouldThrowOnNullParams() {
            assertThrows(IllegalArgumentException.class,
                    () -> EncryptionUtil.generateAndAddApiSecret("key", null));
        }
    }
}
