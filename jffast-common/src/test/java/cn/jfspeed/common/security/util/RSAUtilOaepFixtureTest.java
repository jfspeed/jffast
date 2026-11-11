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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 跨语言 OAEP 一致性 fixture 测试（R-14 范围 A 硬性要求）。
 *
 * <p>密文由 <b>node-forge 1.4.3</b> 一次性脚本离线生成（前端加密库的同源实现）：
 * 固定 2048 位密钥对 + 已知明文 {@code 1762800000000:R14-fix@密码2026}（含中文，
 * 验证 UTF-8 字节口径），加密参数 {@code RSA-OAEP, md=SHA-256, mgf1=SHA-1}——与后端
 * {@link RSAUtil} 显式 {@code OAEPParameterSpec("SHA-256","MGF1",SHA1,empty-label)}
 * 逐一对齐。密钥与密文硬编码于本测试，密文一旦改动本测试必红。
 *
 * <p>fixture 密钥为 PKCS8（与生产密钥 RsaKeyGenerator/JCA KeyPair.getEncoded() 同构；
 * node-forge 默认 privateKeyToPem 是 PKCS1，需经 wrapRsaPrivateKey 包装）。
 * 生成脚本与完整输出见 {@code evidence/logs/r14-fixture-gen.log}（forge 侧已做
 * 「PEM 解析回的密钥」自校验，不用内存对象）。
 *
 * <p>R14 developer#3 补充：六处在役加密方（rsa-encrypt.py / conftest / login_helper /
 * e2e_cleanup / cloud_runtime_probe / smoke.mjs）与独立 JCA 各生成一条同明文密文，
 * Java/Python/Node 三侧互解均逐字还原（含中文）；生成与互解记录见
 * evidence/logs/developer-3-interop.log。
 *
 * @author jffast
 */
class RSAUtilOaepFixtureTest {

    /** node-forge 生成的 2048 位测试密钥对（PKCS8/SPKI，仅测试用，不入任何环境配置） */
    private static final String FIXTURE_PRIVATE_KEY_B64 =
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDAvMaDQGWz1W09UFl9CAJKMWVYWuXIXRppSkBahBrTvHE7A+EyAm0jxMYwnZLW9A0neDWhzuzcnxs0nRjbrGgt7lfOkc8TXCZA/Q+ZRe+kFO7nS+AzVfr6kEo+dBazVEEa943PrJ1uSqU0WYwBac2DAVxbe759dNeM9L9mHmteVC8CDTo+GNkM0v0/ZUlam1BYm/4Lg4MagmOgSplQRX9SkdbszWZow0uIVPmQ3HMz33Z6UyOF32Fm560j5s7j5Ho6TkPa+GUUXWiIYynr+Flsbo3uCY9rGK2cUnftl/dvAHaYEWQVGAGkeHDmvuigoiEsKAQLdQWQAZnKnMbrZYfXAgMBAAECggEAQ0RfYEWkeTw+8QxO/0VBT1WoEks/MUvVQPQqO60pk8aMGlXDTens33P429TS//STdrgMt9l5b5KUMyu1Fk442fb6bF0VUaNysnHf9NWkyraHnvArYp9ahgK/9sYbeiEoy9mkg9ux4mFpATlTlNaxZEygSEmK9UxHQqnU9zYcsQHDYds/NrXv/PGnXPqrfxH0A0ZfNSg9h2Nx5yr1JZWdO1vlS0EwAoH/7ovixpAxgWhp1qfJeVLvhsiOQOFjM+e9KUJR5Fzd0wvNnMHMvJ1whJPsYWtfjT4C3hnQej9Dcw0B8FPe7v/NONKWp11CKH0VGSQrZYB+M9kVz4HvnSz0+QKBgQDovW6qBTf9iqZrfpylIofHOSRD8KiswMu3xidWPAS+e/byIsKZJc7EFOITk4frd+q9wykAQ9ZblQYSGcMRgNcLI+0OGx4afCxG6YWm40oyrp8qvKlB79SsZTGrdjqvJoPQJ0FarD4T9Sn76HLsMoU1a9oMn83mWX5Qx0RbrFB7wwKBgQDT/+RbfB6enOTbdUIYI9MUIMAfx8rQRBoFgZj3nXBYP0X+SJClk8kQknbsiw7NQ8wI9ooK5CZy5C3+AfZMYwfe4v2kY2KZenVUVmbybPMVM/kIxFgcOpF3COWEpR8DHJMc85W458B2sXJBcUOyrRbH7vv/7ifQRTGeiZlduZYGXQKBgEDL7LF9PndiUK8A0w/Oq0q2B/i6Wduu2YjSrET/9LorvTbwookM06IXxlF485CFFF+8o/+k41sgBE6pyXtoCXbai7cvqZPqGlGr1j/+a7vsjffSCrWG1gkhK1D3W6ND79LV+wYJikb4LLYro0NLVXboDg8Rtrt5EwfeUpPLR6UNAoGAKrgHtf+e0+tdmnNYbDDb2dR3om/KCImQjMd+oXbSKpYlboYEz7il7Xb2C9hBlU8yECRN0MAEl2Yvdl3C2cWi0mD/J173gWHRvrriZ7tYiE8+wWt/LcEHiur6BO6Y/zGE23KiGu5oNAKxuUovkBOc4ZDgqsO8thUbWIz1oJNDDekCgYEAi0ITe/F1r0Zm96PZMmUv5uEeRhUc2BY/ZVgLS4GvHdVO9kNc5an+UYx/h6xSnlXYcrfob1857i/WOU+5FZuHaTDcLJ1Q1Jc6OS3qOclHLz5d3pZoupEL3qKnMz2eWVOEYgn/ABX800p/e/zD58zHM6XmlGFz+eWtbCy6o76rqYU=";

    private static final String FIXTURE_PUBLIC_KEY_B64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAwLzGg0Bls9VtPVBZfQgCSjFlWFrlyF0aaUpAWoQa07xxOwPhMgJtI8TGMJ2S1vQNJ3g1oc7s3J8bNJ0Y26xoLe5XzpHPE1wmQP0PmUXvpBTu50vgM1X6+pBKPnQWs1RBGveNz6ydbkqlNFmMAWnNgwFcW3u+fXTXjPS/Zh5rXlQvAg06PhjZDNL9P2VJWptQWJv+C4ODGoJjoEqZUEV/UpHW7M1maMNLiFT5kNxzM992elMjhd9hZuetI+bO4+R6Ok5D2vhlFF1oiGMp6/hZbG6N7gmPaxitnFJ37Zf3bwB2mBFkFRgBpHhw5r7ooKIhLCgEC3UFkAGZypzG62WH1wIDAQAB";

    /** node-forge OAEP(SHA-256, MGF1-SHA1) 加密的固定明文密文（Base64） */
    private static final String FIXTURE_CIPHER_B64 =
            "sPSS+b8RS1ND1xLEoiFfhcjZaYlbv1bqUX+wL0ZcpDQObnVufFuooyfs8ll9zPtQLyn7leavQCYFNMiXyYGIaakVfD7PUCeUif2/OjSzUXCBKCSeNOC23p0HWF7dI9wkPYh3AnxAOpl/GIbJ4mt12mZ9BOO2HbFXaaqaQb+k1mGvjbYpMlmzt5eb9S9BZmDtf3MgX/5feuz9yyMvmp2XUI9pE7vr4bTpyM2O6Jx4r2Q1knKTKC4sxXTculEsLb2JPnOIlthk0+CDtBuxMorSGVQFZ4dNdc2PnUZ6HJLJJTnTaVxqriMwzEZ32dT88HkFtmGkS9PN6/fhN6Cnbue+vA==";

    private static final String FIXTURE_PLAINTEXT = "1762800000000:R14-fix@密码2026";

    @Test
    @DisplayName("node-forge 生成的 OAEP 密文经后端原语解密，明文逐字一致（含中文 UTF-8）")
    void forgeCiphertextDecryptsToExactPlaintext() {
        String plaintext = RSAUtil.decryptByPrivateKeyOaep(FIXTURE_CIPHER_B64, FIXTURE_PRIVATE_KEY_B64);
        assertEquals(FIXTURE_PLAINTEXT, plaintext,
                "node-forge 与后端的 OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须逐一对齐");
    }

    @Test
    @DisplayName("后端原语对同一 fixture 密钥做 OAEP 解密往返（Java 加密 → Java 解密）")
    void javaRoundTripOnFixtureKeyPair() throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(FIXTURE_PUBLIC_KEY_B64)));
        Cipher encrypt = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        encrypt.init(Cipher.ENCRYPT_MODE, publicKey, productionParameterSpec());
        byte[] cipherBytes = encrypt.doFinal(FIXTURE_PLAINTEXT.getBytes(StandardCharsets.UTF_8));

        assertNotNull(cipherBytes);
        assertEquals(256, cipherBytes.length, "2048 位密钥的 OAEP 密文应为 256 字节");

        String decrypted = RSAUtil.decryptByPrivateKeyOaep(
                Base64.getEncoder().encodeToString(cipherBytes), FIXTURE_PRIVATE_KEY_B64);
        assertEquals(FIXTURE_PLAINTEXT, decrypted);
    }

    @Test
    @DisplayName("显式 OAEP 参数逐字段核对：SHA-256 摘要、MGF1-SHA1、空 label")
    void explicitParameterSpecFields() throws Exception {
        OAEPParameterSpec spec = productionParameterSpec();
        assertEquals("SHA-256", spec.getDigestAlgorithm());
        assertEquals("MGF1", spec.getMGFAlgorithm());
        Field sha1 = Class.forName(mgf1ClassName()).getField("SHA1");
        assertEquals(sha1.get(null), spec.getMGFParameters(), "MGF1 哈希必须钉死为 SHA1");
        assertArrayEquals(new byte[0], ((PSource.PSpecified) spec.getPSource()).getValue(), "label 必须为空");
    }

    @Test
    @DisplayName("generateKey 密钥恢复探针切到 OAEP 变换串后仍可产出可用密钥对")
    void generateKeyProbeSurvivesOaepTransformation() {
        java.util.List<String> keys = RSAUtil.generateKey();
        assertNotNull(keys);
        assertEquals(2, keys.size(), "公私钥成对返回");
        // 恢复探针通过 = KeyFactory 能以 OAEP 变换串主算法（RSA）还原密钥
    }

    @Test
    @DisplayName("rsa-encrypt.py（纯 stdlib OAEP）密文经后端原语解密，明文逐字一致")
    void pythonStdlibScriptCiphertextDecryptsToExactPlaintext() {
        String ct = "gCNEKE3YTzOGXtx+UA3Hz5o2ovi0VB3RqTEl3i9gIy2qGEGzm8Z9HepAF3EJHt9MBVWpvdPUgtHtR9QPMcSxuL8nNjNzQc2WqwclaPyyQ4xJ3HV3qhaWOgQw7A54NpV2CW92qv1hGKpNDGyyDK4ojWehqEMUiO8G1OqZKMv3ZPM9UfzhGS7gFMjKUirCBfLtmDWmDqOcmUSH8YiXFbiIQj8B2s/1TMKi/SLKROj6QQXchch2UsGSDdVxnJ68AZteEeW3Pw7UvWqQq0C/w1EREmgaWaOq1JGBl2JDLImGFt/J10G6Q7WyLSq3S3J3EbTKVJkD3Pv/L/dNfkb/nBSBgw==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("conftest（cryptography OAEP）密文经后端原语解密，明文逐字一致")
    void pythonConftestCiphertextDecryptsToExactPlaintext() {
        String ct = "EZYxa5dCFu+HmlrREAW1cSXYwHIA8WHHvZVbDTOCPylK20K36yCktWej0B79ynIAb2yX3uiPw0hNzIvi7JP5iGYdtUwAISSO1+ZinEkjg9tITJF1xTd/8koIFU1+TrlNQ89wplvu4AXTDQKI8FmUys+pb25yGLYxDLLoquBRRRvIDjSjBmVQzk4WNURGZHE0XwEXQV7H1a3hOiuegyNmv0DSRzm92PvQZFpCWb9+HaqwaJniQjjUovi0bMdPruqVMb/LFg+JJCNlWa9gPHI/OvQsWwX8wv7AVCSIaf3AniJi6k28FgcTwbU2D63kZzM95yJkHLBw8J4DJIlXrwJ+mA==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("login_helper（cryptography OAEP）密文经后端原语解密，明文逐字一致")
    void pythonLoginHelperCiphertextDecryptsToExactPlaintext() {
        String ct = "QEc7tYosHmBFICUYhZldy9VxxhS5o2qTOVgmASWA3tVXssQ6i628D3RHKImvMj4caEd20Q7b3ZRHNWnLxaaKBWYom/ucAB3u1O5RlPZoTGhte2Eg01EIBKQo5AlFiGilJ9GjhGKs4EgQJ04OWYxQs+db9lBny/ViFzNXI71dpwNNCdpyD7XmAjSI/UylbKPv3znYN8sutnz0TUdqPPEReYI5UCNz+2yF86ko6tHFAvis09DIIw1L+xaKgi8ut4zUvy7q/wv1MTtIKW0vjadI0PiHJ6htLuZMqlEujdylhnXgYgDTpJbVDZZFq4IZvJt2OtGDDHiDkOV2uBQVA6zdqg==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("e2e_cleanup（cryptography OAEP）密文经后端原语解密，明文逐字一致")
    void pythonE2eCleanupCiphertextDecryptsToExactPlaintext() {
        String ct = "U7ze99+YoF9nYTva6h1ypv18Bf2SYExktoji9JbX1wxCDq1vbwNERbGWa9q0Ky6/Y01RuT446JhhvfnOsQFTzH7R+G9M2EGblYi4yCzgeR17v2ZwFM6xc4BKGTZmZ9IsnzdioXw2nolZQSzNgf57646BJCHfK98zIfocmzD5ld4quYV2hKwQoftd7MeXV6yigXHhHRCsL/03YXqyM/bnCutNQp0b0kfrSbvzqcFCn60b8DnXIQovQMNpVVP8ZtrzFqvmoENaPpLifawX2p2EmsSBkdmLTfXb/BhJSrE5PFv16denQOwmOCKQH66NrnQyrp7ShS/sivi326jY6SIuKw==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("cloud_runtime_probe（纯 stdlib OAEP）密文经后端原语解密，明文逐字一致")
    void pythonCloudProbeCiphertextDecryptsToExactPlaintext() {
        String ct = "EGSNABXm+zOlkH4U0aQyh+1AhsC0xd2ltDl2Zjq7gaUCDKyvXRBDi1ydcY7Pes5YA4TgDrRvC/UxUWNO22qefBz7wvxXn9qfNMT6GLXzkKI9re7MjP26+nC347ehFtpU4NTz6ghSqhU+JCRSjpXgL+F44e97Wd0VUvjp2TR0R7TS5I2LiMhyt+zPtym36hUx3/xtmW5ptUCT4uyFWLCOCxHKn12CXmkYdntpAHPbsiytS8jJBtusmuoIChFsKlGxo3z49MT5yAiH34mCK7WMQbNwap+mzz8K2ddXTR4VkC8u5s+y6+f8KVVcfCCWRV3y41cM9VmITrY3sLBzfwspGQ==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("smoke.mjs 助手（Node 手工 OAEP + RSA_NO_PADDING）密文经后端原语解密，明文逐字一致")
    void nodeSmokeHelperCiphertextDecryptsToExactPlaintext() {
        String ct = "CtYLQASB6Q13YHgqQcVPBkS+MPthXfCibM+DLTAcUheJSS5wDPzQZ90U5wgVK24PBViKWWf8pSomMHQWj9rfI4C1sMm0OskMJ1y4rdKWQ8Z2wdzqsyDEDscW7tyua+A1XB9kc6T3FjHvEsPL72GwvCDYqVydOo+oQ0ghGFYs+mkdIVgZ4i/fPCi9curXfluZb7TOV5OA8QbI69y2J0C722OEGR8qWXYxdunrLqgfgP+EvDubjfKppIikVTUSk3zkfBm7kYByraf3DsxMqbrf07r1PDyLOegBjX2qbGT2bsVe9+vWuUorTkE/K+6cEHvMi0RVpuMpDMlKHZEHD9hLMQ==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    @Test
    @DisplayName("独立 JCA（jshell 显式 OAEPParameterSpec）密文经后端原语解密，明文逐字一致")
    void javaJcaCiphertextDecryptsToExactPlaintext() {
        String ct = "Bn6YDmCYj66s/kSNBSBP3w3E3iBOQnpwvacETSRFP/CAJOayp9ncUUsXGNiJXKwTErnJQ/P/HXQg0Nr+hy2R5Xt/MqVOHEcxy4uuCIKZx2MwNblMB2g1f4FKmH3+QY6GQnhNpbZHsqnYlNvpmu7ljfiR0Q5z9yuQjEXtCDmXWzaCJoA3ZE++o11he69BG/jHVGF3mddWEXRWbttkVGkfjW9k9H+bDSAUj7uMBAqDKmXkhrx14Qh1hscmVbrnquwgtfXJ8gMRRFDm5PrmQVDGDqjXouBeM/DF1kpH50HIX0wo03cVt5quNYGW2Ipj/nG+ZREWUHhCVzErYgaPaXqBLQ==";
        assertEquals(FIXTURE_PLAINTEXT, RSAUtil.decryptByPrivateKeyOaep(ct, FIXTURE_PRIVATE_KEY_B64),
                "OAEP 参数（SHA-256 + MGF1-SHA1 + 空 label）与 UTF-8 字节口径必须与后端逐一对齐");
    }

    private static String mgf1ClassName() {
        for (String name : new String[] {"javax.crypto.spec.MGF1ParameterSpec",
                "java.security.spec.MGF1ParameterSpec"}) {
            try {
                Class.forName(name);
                return name;
            } catch (ClassNotFoundException ignored) {
                // 尝试下一个布局
            }
        }
        throw new IllegalStateException("MGF1ParameterSpec 不可用（JDK 环境异常）");
    }

    /** 与 RSAUtil 内部完全同参：双路径解析本机 JDK 的 MGF1ParameterSpec.SHA1 */
    private static OAEPParameterSpec productionParameterSpec() throws Exception {
        Field sha1 = Class.forName(mgf1ClassName()).getField("SHA1");
        return new OAEPParameterSpec("SHA-256", "MGF1", (AlgorithmParameterSpec) sha1.get(null),
                PSource.PSpecified.DEFAULT);
    }
}
