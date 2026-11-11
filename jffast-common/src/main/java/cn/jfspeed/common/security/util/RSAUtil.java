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

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import org.springframework.stereotype.Component;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.AsymmetricAlgorithm;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.RSA;
import cn.jfspeed.common.core.config.JfFastProperties;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
@Component
public class RSAUtil {

    /**
     * 登录密码路径的 OAEP 变换串：digest SHA-256 + MGF1。
     * <p>注意 JDK 契约：{@code OAEPWithSHA-256AndMGF1Padding} 在 SunJCE 下 MGF1 默认即 SHA-1，
     * 但本类不依赖 provider 默认值——解密时 {@link Cipher#init(int, java.security.Key, java.security.spec.AlgorithmParameterSpec)}
     * 显式传入 {@link #PWD_OAEP_PARAMETER_SPEC} 钉死参数，与前端 node-forge 的
     * {@code md=SHA-256 / mgf1=SHA-1} 逐一对齐（R-14 范围 A）。
     * <p>只用于登录密码路径：四个通用加解密方法保持 PKCS1（ApiUtil 通道语义零变化）。
     */
    private static final String PWD_OAEP_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    /**
     * 登录密码路径的 OAEP 显式参数：digest SHA-256、MGF1-SHA1、空 label。
     * <p>MGF1 参数只取 {@code MGF1ParameterSpec.SHA1}（摘要名容器，语义与类所在包无关）。
     * 个别 JDK 环境把该类置于 {@code java.security.spec}，OpenJDK 上游标准位置是
     * {@code javax.crypto.spec}——这里双路径解析，保证两种布局下都显式钉死参数，
     * 不依赖 provider 对变换串的默认值。
     */
    private static final OAEPParameterSpec PWD_OAEP_PARAMETER_SPEC = buildPwdOaepParameterSpec();

    private static OAEPParameterSpec buildPwdOaepParameterSpec() {
        for (String className : new String[] {
                "javax.crypto.spec.MGF1ParameterSpec",      // OpenJDK 上游标准位置
                "java.security.spec.MGF1ParameterSpec"}) {  // 部分 JDK 环境的变体位置
            try {
                Object sha1 = Class.forName(className).getField("SHA1").get(null);
                return new OAEPParameterSpec("SHA-256", "MGF1", (AlgorithmParameterSpec) sha1, PSource.PSpecified.DEFAULT);
            } catch (ReflectiveOperationException ignored) {
                // 该布局不存在，尝试下一个位置
            }
        }
        throw new IllegalStateException("MGF1ParameterSpec 不可用：javax.crypto.spec 与 java.security.spec 均未找到（JDK 环境异常）");
    }

    /** 密码载荷中「时间戳」与「密码」的分隔符 */
    private static final char PWD_PAYLOAD_SEPARATOR = ':';

    /** 密码密文允许的最大存活时间：5 分钟 */
    private static final long PWD_PAYLOAD_MAX_AGE_MILLIS = 5 * 60 * 1000L;

    /** 允许的客户端时钟提前量：1 分钟（容忍客户端与服务端时钟偏差） */
    private static final long PWD_PAYLOAD_CLOCK_SKEW_MILLIS = 60 * 1000L;

    private static JfFastProperties jfFastProperties;

    @Resource
    public void setJfFastProperties(JfFastProperties jfFastProperties) {
        RSAUtil.jfFastProperties = jfFastProperties;
    }

    public static HashMap<String, String> initKey(){
        RSA rsa_o = SecureUtil.rsa();
        String priKey = rsa_o.getPrivateKeyBase64();
        String  pubKey = rsa_o.getPublicKeyBase64();
        HashMap<String, String> map = new HashMap<>();
        map.put("priKey", priKey);
        map.put("pubKey", pubKey);
        return map;
    }

    public static String encryptByPublicKey(String content, String publicKeyStr) {
        RSA rsa = new RSA(null, publicKeyStr);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] encryptBytes = rsa.encrypt(contentBytes, KeyType.PublicKey);
        return Base64.encode(encryptBytes);
    }

    public static String decryptByPrivateKey(String encryptContent, String privateKeyStr) {
        RSA rsa = new RSA(privateKeyStr, null);
        byte[] encryptBytes = Base64.decode(encryptContent);
        byte[] decryptBytes = rsa.decrypt(encryptBytes, KeyType.PrivateKey);
        return new String(decryptBytes, StandardCharsets.UTF_8);
    }

    public static String encryptByPrivateKey(String content, String privateKeyStr) {
        RSA rsa = new RSA(privateKeyStr, null);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] encryptBytes = rsa.encrypt(contentBytes, KeyType.PrivateKey);
        return Base64.encode(encryptBytes);
    }

    public static String decryptByPublicKey(String encryptContent, String publicKeyStr) {
        RSA rsa = new RSA(null, publicKeyStr);
        byte[] encryptBytes = Base64.decode(encryptContent);
        byte[] decryptBytes = rsa.decrypt(encryptBytes, KeyType.PublicKey);
        return new String(decryptBytes, StandardCharsets.UTF_8);
    }

    /**
     * PKCS1 v1.5 解密（遗留方法，当前零消费方）。
     * <p>⚠️ 与登录密码前端加密（OAEP）不配套——登录密码一律走
     * {@link #decryptPwdWithTimestamp(String)}，勿把密码密文传进本方法。
     */
    public static String decryptPwd(String encryptPwd){
        if (jfFastProperties == null) {
            throw new IllegalStateException("RSAUtil 尚未完成初始化，请检查 jffast.rsa 配置是否加载");
        }
        if (StrUtil.isEmpty(jfFastProperties.getPrivateKey())) {
            throw new RuntimeException("RSA私钥未配置，请检查环境变量 RSA_PRIVATE_KEY 或运行 RsaKeyGenerator 生成密钥对");
        }
        return decryptByPrivateKey(encryptPwd, jfFastProperties.getPrivateKey());
    }

    public static String encryptPwd(String pwd){
        return encryptByPublicKey(pwd, jfFastProperties.getPublicKey());
    }

    /**
     * 登录密码路径的 OAEP 解密（包内可见供单测固定跨语言 fixture）。
     * <p>密钥恢复用 {@code KeyFactory("RSA")} + PKCS8——RSA 密钥本身与 padding 无关，
     * padding 由 {@link Cipher#init} 的显式 {@link OAEPParameterSpec} 决定。
     *
     * @param encryptContent Base64(RSA-OAEP 密文)
     * @param privateKeyStr  Base64(PKCS8 私钥)
     * @return UTF-8 明文
     */
    static String decryptByPrivateKeyOaep(String encryptContent, String privateKeyStr) {
        try {
            PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(privateKeyStr)));
            Cipher cipher = Cipher.getInstance(PWD_OAEP_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, PWD_OAEP_PARAMETER_SPEC);
            byte[] decrypted = cipher.doFinal(Base64.decode(encryptContent));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("OAEP 解密失败（算法参数与前端约定不一致或密文损坏）", e);
        }
    }

    /**
     * 密码密文的明文格式：{@code <毫秒时间戳>:<密码>}。
     * <p>
     * 前端加密时拼上时间戳（见 shared/src/utils/crypto.js 的 encryptPassword / rsaEncrypt），
     * 服务端解密后校验其新鲜度。加密算法为 RSA-OAEP（SHA-256 摘要 + MGF1-SHA1，显式参数，
     * 与前端 node-forge 逐一对齐）；载荷 UTF-8 字节数上限 190（2048 位密钥 + OAEP-SHA256
     * 的明文上限 = 256 - 2×32 - 2），前端 crypto.js 有同口径守卫。目的与历史一致：
     * PKCS#1 v1.5 是确定性加密、可无限期重放，时间戳把重放窗口收敛到几分钟；
     * OAEP 同时消除 Bleichenbacher 填充预言攻击面。
     *
     * @param encryptPwd 前端用公钥加密后的密码密文
     * @return 明文密码
     * @throws BusinessException 格式错误或已过期
     * @throws IllegalStateException RSAUtil 未初始化、私钥未配置或 OAEP 解密失败
     */
    public static String decryptPwdWithTimestamp(String encryptPwd) {
        if (jfFastProperties == null) {
            throw new IllegalStateException("RSAUtil 尚未完成初始化，请检查 jffast.rsa 配置是否加载");
        }
        if (StrUtil.isEmpty(jfFastProperties.getPrivateKey())) {
            throw new RuntimeException("RSA私钥未配置，请检查环境变量 RSA_PRIVATE_KEY 或运行 RsaKeyGenerator 生成密钥对");
        }
        String payload = decryptByPrivateKeyOaep(encryptPwd, jfFastProperties.getPrivateKey());
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "密码解析失败");
        }

        int idx = payload.indexOf(PWD_PAYLOAD_SEPARATOR);
        if (idx <= 0 || idx == payload.length() - 1) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "密码格式错误");
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(payload.substring(0, idx).trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "密码格式错误");
        }

        long age = System.currentTimeMillis() - timestamp;
        if (age > PWD_PAYLOAD_MAX_AGE_MILLIS || age < -PWD_PAYLOAD_CLOCK_SKEW_MILLIS) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "登录信息已过期，请重新提交");
        }

        return payload.substring(idx + 1);
    }

    public static List<String> generateKey() {
        ArrayList<String> list = new ArrayList<>();
        KeyPair keyPair = SecureUtil.generateKeyPair(AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(), 2048);
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        String publicKeyStr = Base64.encode(publicKey.getEncoded());
        String privateKeyStr = Base64.encode(privateKey.getEncoded());

        // 密钥内容（尤其私钥）不落日志：debug 级别也不行，日志被收集即泄露
        log.debug("RSA 密钥对已生成");

        try {
            // 探针与登录密码路径同参数族（R-14 范围 A）。RSA 密钥恢复走 KeyFactory 主算法，
            // 与 padding 无关——这里切到 OAEP 变换串仅为让探针口径与密码路径一致。
            PublicKey restoredPubKey = SecureUtil.generatePublicKey(
                    PWD_OAEP_TRANSFORMATION,
                    Base64.decode(publicKeyStr)
            );
            PrivateKey restoredPriKey = SecureUtil.generatePrivateKey(
                    PWD_OAEP_TRANSFORMATION,
                    Base64.decode(privateKeyStr)
            );

            boolean pubKeyValid = Base64.encode(restoredPubKey.getEncoded()).equals(publicKeyStr);
            boolean priKeyValid = Base64.encode(restoredPriKey.getEncoded()).equals(privateKeyStr);

            log.debug("密钥恢复验证：公钥有效={}, 私钥有效={}", pubKeyValid, priKeyValid);
            list.add(publicKeyStr);
            list.add(privateKeyStr);
        } catch (Exception e) {
            log.error("密钥恢复失败：{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }

        return list;
    }
}
