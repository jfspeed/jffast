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

import cn.jfspeed.common.security.util.AESUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class AESUtilTest {

    @BeforeAll
    static void init() {
        AESUtil.init("O2BEeIv399qHQNhD6aGW8R8DEj4bqHXm", "0000000000000000");
    }

    @Nested
    @DisplayName("CBC 模式")
    class CBC {

        @Test
        @DisplayName("加密后解密应恢复原文 (ZeroPadding)")
        void shouldRoundTripWithZeroPadding() {
            String plain = "Hello, jffast!";
            String encrypted = AESUtil.encryptFromString(plain, Mode.CBC, Padding.ZeroPadding);
            assertNotNull(encrypted);
            assertNotEquals(plain, encrypted);

            String decrypted = AESUtil.decryptFromString(encrypted, Mode.CBC, Padding.ZeroPadding);
            assertEquals(plain, decrypted);
        }

        @Test
        @DisplayName("加密后解密应恢复原文 (PKCS5Padding)")
        void shouldRoundTripWithPKCS5() {
            String plain = "测试中文内容";
            String encrypted = AESUtil.encryptFromString(plain, Mode.CBC, Padding.PKCS5Padding);
            String decrypted = AESUtil.decryptFromString(encrypted, Mode.CBC, Padding.PKCS5Padding);
            assertEquals(plain, decrypted);
        }
    }

    @Nested
    @DisplayName("ECB 模式")
    class ECB {

        @Test
        @DisplayName("加密后解密应恢复原文")
        void shouldRoundTrip() {
            String plain = "ECB mode test";
            String encrypted = AESUtil.encryptFromString(plain, Mode.ECB, Padding.ZeroPadding);
            String decrypted = AESUtil.decryptFromString(encrypted, Mode.ECB, Padding.ZeroPadding);
            assertEquals(plain, decrypted);
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空字符串应正常加密解密")
        void shouldHandleEmptyString() {
            String plain = "";
            String encrypted = AESUtil.encryptFromString(plain, Mode.CBC, Padding.ZeroPadding);
            String decrypted = AESUtil.decryptFromString(encrypted, Mode.CBC, Padding.ZeroPadding);
            assertEquals(plain, decrypted);
        }

        @Test
        @DisplayName("长文本应正常加密解密")
        void shouldHandleLongText() {
            String plain = "a".repeat(1000);
            String encrypted = AESUtil.encryptFromString(plain, Mode.CBC, Padding.PKCS5Padding);
            String decrypted = AESUtil.decryptFromString(encrypted, Mode.CBC, Padding.PKCS5Padding);
            assertEquals(plain, decrypted);
        }
    }
}
