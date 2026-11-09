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

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 文件签名 URL 的票据编解码：{@code base64url(payload) + "." + HMAC-SHA256(body)}。
 *
 * <p>算法与 {@code SignedStateUtil} 同源（签名覆盖编码后的 body 本身，避免字段拼接的边界歧义），
 * 但**不依赖 starter-social**：本类放在 jffast-common，供 jffast-system 的文件归属服务使用，
 * 不反向依赖 starter-file，也不引入 starter-social。
 *
 * <p>payload 形态：{@code {"id":"<fileId>","exp":<epochSeconds>,"tid":<tenantId>}}。
 * {@code id} 一律是**字符串**（雪花 ID 19 位，超出 JS 安全整数范围，数字型会在前端丢精度）；
 * {@code tid} 是数字（租户 ID，0 = 平台/单租户）。
 *
 * <p>失败口径统一为「文件不存在或已清理」——不区分「签名错 / 过期 / 记录不存在」，
 * 避免用返回值差异枚举出文件是否存在（防枚举）。
 *
 * @author jffast
 */
public final class FileUrlSignUtil {

    private static final char SEPARATOR = '.';

    private static final String KEY_ID = "id";
    private static final String KEY_EXP = "exp";
    private static final String KEY_TID = "tid";

    /** 统一的失败提示：与「记录不存在」完全一致，防枚举 */
    public static final String INVALID_MESSAGE = "文件不存在或已清理";

    private FileUrlSignUtil() {
    }

    /**
     * 签发票据。
     *
     * @param fileId            文件 ID（雪花 ID，序列化时转字符串）
     * @param tenantId          归属租户（0 = 平台/单租户）
     * @param expireEpochSecond 过期时间（epoch 秒，应用侧时钟，不依赖 DB 会话时区）
     * @param secret            HMAC 密钥
     */
    public static String sign(long fileId, long tenantId, long expireEpochSecond, String secret) {
        JSONObject payload = JSONUtil.createObj();
        payload.set(KEY_ID, String.valueOf(fileId));
        payload.set(KEY_EXP, expireEpochSecond);
        payload.set(KEY_TID, tenantId);
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return body + SEPARATOR + digest(body, secret);
    }

    /**
     * 验签并解析票据；签名缺失/不匹配/格式错/已过期一律抛业务异常（HTTP 200 + 业务码 400）。
     */
    public static FileUrlTicket verify(String ticket, String secret) {
        String payload = verifyAndGetPayload(ticket, secret);
        JSONObject obj;
        try {
            obj = JSONUtil.parseObj(payload);
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        Object rawId = obj.get(KEY_ID);
        if (rawId == null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        long fileId;
        try {
            fileId = Long.parseLong(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        Long expireAt = obj.getLong(KEY_EXP);
        if (expireAt == null) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        if (expireAt <= System.currentTimeMillis() / 1000L) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        Long tenantId = obj.getLong(KEY_TID);
        return new FileUrlTicket(fileId, tenantId == null ? 0L : tenantId, expireAt);
    }

    /**
     * 校验签名并返回原始 payload；签名缺失/不匹配时抛 400，绝不返回未验签内容。
     */
    public static String verifyAndGetPayload(String signed, String secret) {
        if (StrUtil.isBlank(signed)) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        int index = signed.lastIndexOf(SEPARATOR);
        if (index <= 0 || index == signed.length() - 1) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        String body = signed.substring(0, index);
        String signature = signed.substring(index + 1);
        byte[] expected = digest(body, secret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
        try {
            return new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), INVALID_MESSAGE);
        }
    }

    private static String digest(String body, String secret) {
        if (StrUtil.isBlank(secret)) {
            throw new BusinessException(FrameworkErrorCode.SYSTEM_ERROR.getCode(), "文件签名密钥未配置");
        }
        return SecureUtil.hmacSha256(secret).digestHex(body);
    }

    /**
     * 票据解析结果。
     *
     * @param fileId   文件 ID
     * @param tenantId 票据里绑定的租户（与记录 tenant_id 不一致时一律拒绝）
     * @param expireAt 过期时间（epoch 秒）
     */
    public record FileUrlTicket(long fileId, long tenantId, long expireAt) {
    }
}
