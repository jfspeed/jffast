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
package cn.jfspeed.oauth2.client.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth2 令牌（客户端换发结果），以 JSON 形式存 Redis 缓存。
 * <p>
 * 时间字段用 epoch 毫秒存储，避免 LocalDateTime 序列化兼容问题。
 *
 * @author jffast
 */
@Data
public class OAuth2ClientToken implements Serializable {

    private static final long serialVersionUID = 1L;

    private String accessToken;
    private String tokenType;
    private String refreshToken;
    private Long accessTokenExpiresIn;
    /** 签发时间(epoch 毫秒) */
    private long issuedAt;
    /** 过期时间(epoch 毫秒) */
    private long expiresAt;
    /** 原始响应 JSON */
    private String raw;

    public OAuth2ClientToken() {
    }

    /** 是否已过期 */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    /** 剩余有效秒数（至少 1 秒） */
    public long remainingSeconds() {
        long remain = (expiresAt - System.currentTimeMillis()) / 1000;
        return remain > 0 ? remain : 1;
    }
}
