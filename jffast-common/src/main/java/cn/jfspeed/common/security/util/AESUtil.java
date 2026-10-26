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


import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;


/**
 * AES 加解密工具。
 * <p>
 * 注意：生产环境务必通过环境变量或 {@link #init(String, String)} 设置密钥，
 * 禁止在代码中硬编码密钥。
 * </p>
 *
 * @author jffast
 */
public class AESUtil {
    private static String encodeKey;
    private static String ivKey;

    static {
        encodeKey = System.getenv("JFFAST_AES_KEY");
        ivKey = System.getenv("JFFAST_AES_IV");
        if (encodeKey == null) {
            encodeKey = System.getProperty("jffast.aes.key");
        }
        if (ivKey == null) {
            ivKey = System.getProperty("jffast.aes.iv");
        }
    }

    public static void init(String key, String iv) {
        encodeKey = key;
        ivKey = iv;
    }

    private static void ensureInitialized() {
        if (encodeKey == null || ivKey == null) {
            throw new IllegalStateException("AES 密钥未配置，请先调用 AESUtil.init(key, iv) 或设置环境变量 JFFAST_AES_KEY / JFFAST_AES_IV");
        }
    }

    public static String encryptFromString(String data, Mode mode, Padding padding) {
        ensureInitialized();
        AES aes;
        if (Mode.CBC == mode) {
            aes = new AES(mode, padding,
                    new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(ivKey.getBytes(StandardCharsets.UTF_8)));
        } else {
            aes = new AES(mode, padding,
                    new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES"));
        }
        return aes.encryptBase64(data, StandardCharsets.UTF_8);
    }

    public static String decryptFromString(String data, Mode mode, Padding padding) {
        ensureInitialized();
        AES aes;
        if (Mode.CBC == mode) {
            aes = new AES(mode, padding,
                    new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(ivKey.getBytes(StandardCharsets.UTF_8)));
        } else {
            aes = new AES(mode, padding,
                    new SecretKeySpec(encodeKey.getBytes(StandardCharsets.UTF_8), "AES"));
        }
        byte[] decryptDataBase64 = aes.decrypt(data);
        return new String(decryptDataBase64, StandardCharsets.UTF_8);
    }

}
