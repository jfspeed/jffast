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
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
     * 密码密文的明文格式：{@code <毫秒时间戳>:<密码>}。
     * <p>
     * 前端加密时拼上时间戳（见 shared/src/utils/rsa-key.js 的 encryptPassword），
     * 服务端解密后校验其新鲜度。目的：RSA PKCS#1 v1.5 是确定性加密，同样的明文每次
     * 产生同样的密文，截获一次登录报文即可无限期重放；加上时间窗后，被截获的密文只在
     * 几分钟内可用，重放窗口从"永久"收敛为"几分钟"。
     *
     * @param encryptPwd 前端用公钥加密后的密码密文
     * @return 明文密码
     * @throws BusinessException 格式错误或已过期
     */
    public static String decryptPwdWithTimestamp(String encryptPwd) {
        String payload = decryptPwd(encryptPwd);
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
            PublicKey restoredPubKey = SecureUtil.generatePublicKey(
                    AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(),
                    Base64.decode(publicKeyStr)
            );
            PrivateKey restoredPriKey = SecureUtil.generatePrivateKey(
                    AsymmetricAlgorithm.RSA_ECB_PKCS1.getValue(),
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
