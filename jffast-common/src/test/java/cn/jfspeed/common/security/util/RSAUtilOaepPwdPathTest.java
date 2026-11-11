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

import cn.jfspeed.common.core.config.JfFastProperties;
import cn.jfspeed.common.core.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录密码路径 OAEP 切换（R-14 范围 A）的生产路径测试：OAEP 加密载荷进
 * {@link RSAUtil#decryptPwdWithTimestamp(String)}（登录密码唯一解密入口），
 * 钉住明文往返、时间窗拒收、格式拒收与 190 字节载荷边界。
 *
 * <p>加密侧在测试内用与前端 node-forge 完全一致的显式参数
 * {@code OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSpecified.DEFAULT)}
 * 构造密文——参数对不齐时本测试必红。fixture 密钥对由 node-forge 一次性脚本生成
 * （见 evidence/logs/r14-fixture-gen.log），与 {@link RSAUtilOaepFixtureTest} 共用同一对。
 *
 * @author jffast
 */
class RSAUtilOaepPwdPathTest {

    /** 测试专用密钥对（node-forge 生成，2048 位，不入任何环境配置） */
    private static final String FIXTURE_PRIVATE_KEY_B64 =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDAvMaDQGWz1W09UFl9CAJKMWVYWuXIXRppSkBahBrTvHE7A+EyAm0jxMYwnZLW9A0neDWhzuzcnxs0nRjbrGgt7lfOkc8TXCZA/Q+ZRe+kFO7nS+AzVfr6kEo+dBazVEEa943PrJ1uSqU0WYwBac2DAVxbe759dNeM9L9mHmteVC8CDTo+GNkM0v0/ZUlam1BYm/4Lg4MagmOgSplQRX9SkdbszWZow0uIVPmQ3HMz33Z6UyOF32Fm560j5s7j5Ho6TkPa+GUUXWiIYynr+Flsbo3uCY9rGK2cUnftl/dvAHaYEWQVGAGkeHDmvuigoiEsKAQLdQWQAZnKnMbrZYfXAgMBAAECggEAQ0RfYEWkeTw+8QxO/0VBT1WoEks/MUvVQPQqO60pk8aMGlXDTens33P429TS//STdrgMt9l5b5KUMyu1Fk442fb6bF0VUaNysnHf9NWkyraHnvArYp9ahgK/9sYbeiEoy9mkg9ux4mFpATlTlNaxZEygSEmK9UxHQqnU9zYcsQHDYds/NrXv/PGnXPqrfxH0A0ZfNSg9h2Nx5yr1JZWdO1vlS0EwAoH/7ovixpAxgWhp1qfJeVLvhsiOQOFjM+e9KUJR5Fzd0wvNnMHMvJ1whJPsYWtfjT4C3hnQej9Dcw0B8FPe7v/NONKWp11CKH0VGSQrZYB+M9kVz4HvnSz0+QKBgQDovW6qBTf9iqZrfpylIofHOSRD8KiswMu3xidWPAS+e/byIsKZJc7EFOITk4frd+q9wykAQ9ZblQYSGcMRgNcLI+0OGx4afCxG6YWm40oyrp8qvKlB79SsZTGrdjqvJoPQJ0FarD4T9Sn76HLsMoU1a9oMn83mWX5Qx0RbrFB7wwKBgQDT/+RbfB6enOTbdUIYI9MUIMAfx8rQRBoFgZj3nXBYP0X+SJClk8kQknbsiw7NQ8wI9ooK5CZy5C3+AfZMYwfe4v2kY2KZenVUVmbybPMVM/kIxFgcOpF3COWEpR8DHJMc85W458B2sXJBcUOyrRbH7vv/7ifQRTGeiZlduZYGXQKBgEDL7LF9PndiUK8A0w/Oq0q2B/i6Wduu2YjSrET/9LorvTbwookM06IXxlF485CFFF+8o/+k41sgBE6pyXtoCXbai7cvqZPqGlGr1j/+a7vsjffSCrWG1gkhK1D3W6ND79LV+wYJikb4LLYro0NLVXboDg8Rtrt5EwfeUpPLR6UNAoGAKrgHtf+e0+tdmnNYbDDb2dR3om/KCImQjMd+oXbSKpYlboYEz7il7Xb2C9hBlU8yECRN0MAEl2Yvdl3C2cWi0mD/J173gWHRvrriZ7tYiE8+wWt/LcEHiur6BO6Y/zGE23KiGu5oNAKxuUovkBOc4ZDgqsO8thUbWIz1oJNDDekCgYEAi0ITe/F1r0Zm96PZMmUv5uEeRhUc2BY/ZVgLS4GvHdVO9kNc5an+UYx/h6xSnlXYcrfob1857i/WOU+5FZuHaTDcLJ1Q1Jc6OS3qOclHLz5d3pZoupEL3qKnMz2eWVOEYgn/ABX800p/e/zD58zHM6XmlGFz+eWtbCy6o76rqYU=";

    private static final String FIXTURE_PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwLzGg0Bls9VtPVBZfQgCSjFlWFrlyF0aaUpAWoQa07xxOwPhMgJtI8TGMJ2S1vQNJ3g1oc7s3J8bNJ0Y26xoLe5XzpHPE1wmQP0PmUXvpBTu50vgM1X6+pBKPnQWs1RBGveNz6ydbkqlNFmMAWnNgwFcW3u+fXTXjPS/Zh5rXlQvAg06PhjZDNL9P2VJWptQWJv+C4ODGoJjoEqZUEV/UpHW7M1maMNLiFT5kNxzM992elMjhd9hZuetI+bO4+R6Ok5D2vhlFF1oiGMp6/hZbG6N7gmPaxitnFJ37Zf3bwB2mBFkFRgBpHhw5r7ooKIhLCgEC3UFkAGZypzG62WH1wIDAQAB";

    /** 与前端 crypto.js 同口径的载荷上限：2048 位 + OAEP-SHA256（hLen=32）→ 256-2*32-2=190 字节 */
    private static final int PAYLOAD_MAX_BYTES = 190;

    @BeforeEach
    void setUp() {
        JfFastProperties props = new JfFastProperties();
        props.setPrivateKey(FIXTURE_PRIVATE_KEY_B64);
        props.setPublicKey(FIXTURE_PUBLIC_KEY_B64);
        new RSAUtil().setJfFastProperties(props);
    }

    @AfterEach
    void tearDown() {
        new RSAUtil().setJfFastProperties(null);
    }

    /**
     * 测试侧独立解析 MGF1ParameterSpec.SHA1：个别 JDK 环境将该类置于 java.security.spec，
     * OpenJDK 上游标准位置为 javax.crypto.spec；两者只是摘要名容器，语义相同。
     * 测试不引用 RSAUtil 的内部实现，独立构造同参数密文做交叉验证。
     */
    private static AlgorithmParameterSpec mgf1Sha1() throws Exception {
        for (String className : new String[] {
                "javax.crypto.spec.MGF1ParameterSpec",
                "java.security.spec.MGF1ParameterSpec"}) {
            try {
                Field sha1 = Class.forName(className).getField("SHA1");
                return (AlgorithmParameterSpec) sha1.get(null);
            } catch (ReflectiveOperationException ignored) {
                // 该布局不存在，尝试下一个位置
            }
        }
        throw new IllegalStateException("MGF1ParameterSpec 不可用（JDK 环境异常）");
    }

    /** 测试侧 OAEP 加密：显式参数与后端解密、前端 node-forge 三方一致（含 UTF-8 字节） */
    private static String oaepEncryptToBase64(String plainText) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(FIXTURE_PUBLIC_KEY_B64)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey,
                new OAEPParameterSpec("SHA-256", "MGF1", mgf1Sha1(), PSource.PSpecified.DEFAULT));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Test
    @DisplayName("OAEP 加密的活载荷经生产入口解密往返一致（含中文密码，UTF-8 口径）")
    void oaepRoundTripThroughProductionPath() throws Exception {
        String password = "R14-Pwd@密码2026";
        String payload = System.currentTimeMillis() + ":" + password;

        String cipherText = oaepEncryptToBase64(payload);
        assertEquals(password, RSAUtil.decryptPwdWithTimestamp(cipherText));
    }

    @Test
    @DisplayName("超过 5 分钟时间窗的密文被拒收（登录信息已过期）")
    void rejectsStaleTimestamp() throws Exception {
        String stalePayload = (System.currentTimeMillis() - 10 * 60 * 1000L) + ":" + "Stale@密码";
        String cipherText = oaepEncryptToBase64(stalePayload);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> RSAUtil.decryptPwdWithTimestamp(cipherText));
        assertTrue(ex.getMessage().contains("登录信息已过期"), "实际消息：" + ex.getMessage());
    }

    @Test
    @DisplayName("时间戳段非法的载荷被拒收（密码格式错误）")
    void rejectsMalformedPayload() throws Exception {
        String cipherText = oaepEncryptToBase64("not-a-timestamp:pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> RSAUtil.decryptPwdWithTimestamp(cipherText));
        assertTrue(ex.getMessage().contains("密码格式错误"), "实际消息：" + ex.getMessage());
    }

    @Test
    @DisplayName("载荷 190 字节边界可往返，191 字节在加密层即被 OAEP 上限拒绝")
    void payloadSizeBoundary() {
        // 13(时间戳) + 1(冒号) = 14 字节，密码补足到 190-14=176 字节
        String maxPassword = "A".repeat(PAYLOAD_MAX_BYTES - 14);
        String maxPayload = System.currentTimeMillis() + ":" + maxPassword;
        assertEquals(PAYLOAD_MAX_BYTES, maxPayload.getBytes(StandardCharsets.UTF_8).length);

        String cipherText = assertDoesNotThrow(() -> oaepEncryptToBase64(maxPayload));
        assertEquals(maxPassword, RSAUtil.decryptPwdWithTimestamp(cipherText));

        // 191 字节：OAEP-SHA256 明文上限在加密层直接拒绝（前端守卫的存在依据）
        String overPassword = "A".repeat(PAYLOAD_MAX_BYTES - 14 + 1);
        String overPayload = System.currentTimeMillis() + ":" + overPassword;
        assertThrows(Exception.class, () -> oaepEncryptToBase64(overPayload));
    }
}
