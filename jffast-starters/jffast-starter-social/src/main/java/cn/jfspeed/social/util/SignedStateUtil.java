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
package cn.jfspeed.social.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * OAuth state / 绑定票据的签名编解码：{@code base64url(payload) + "." + HMAC-SHA256(body)}。
 * <p>
 * 签名覆盖编码后的 body 本身，避免字段拼接带来的边界歧义。
 *
 * @author jffast
 */
public final class SignedStateUtil {

    private static final char SEPARATOR = '.';

    private SignedStateUtil() {
    }

    public static String sign(String payload, String secret) {
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + SEPARATOR + digest(body, secret);
    }

    /**
     * 校验签名并返回原始 payload；签名缺失/不匹配时抛 400，绝不返回未验签内容。
     */
    public static String verifyAndGetPayload(String signed, String secret) {
        if (StrUtil.isBlank(signed)) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 不能为空");
        }
        int index = signed.lastIndexOf(SEPARATOR);
        if (index <= 0 || index == signed.length() - 1) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 格式不正确");
        }
        String body = signed.substring(0, index);
        String signature = signed.substring(index + 1);
        byte[] expected = digest(body, secret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 签名校验失败");
        }
        try {
            return new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 格式不正确");
        }
    }

    private static String digest(String body, String secret) {
        if (StrUtil.isBlank(secret)) {
            throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "社交登录未配置签名密钥");
        }
        return SecureUtil.hmacSha256(secret).digestHex(body);
    }
}
