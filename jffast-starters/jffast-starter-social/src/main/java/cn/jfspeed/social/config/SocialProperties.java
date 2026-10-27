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
package cn.jfspeed.social.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 社交账号绑定 Starter 配置属性
 *
 * <pre>
 * jffast:
 *   social:
 *     enabled: false           # 是否启用社交账号绑定（默认 false）
 *     providers:
 *       github:
 *         client-id: xxx
 *         client-secret: xxx
 *         redirect-uri: http://localhost:7201/social/callback
 *       gitee:
 *         client-id: xxx
 *         client-secret: xxx
 *         redirect-uri: http://localhost:7201/social/callback
 * </pre>
 *
 * <p>{@code redirect-uri} 是**社交平台回跳到的地址**，通常是前端页面而不是后端接口：
 * 授权码只能用一次且必须由后端兑换成 access_token，前端页面拿到 code/state 后把它交回
 * 发起绑定的那个同源窗口，由那个窗口调 {@code GET /social/callback} 完成兑换。
 * 平台标识（provider）由服务端从**签名 state** 里解码得到，回跳地址不需要带 provider 参数。
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.social")
public class SocialProperties {

    /** 是否启用社交账号绑定，默认 false */
    private boolean enabled = false;

    /**
     * OAuth state / 绑定票据的 HMAC 密钥；为空时由各 provider 的 client-secret 派生。
     * 多实例部署请显式配置成同一值。
     */
    private String stateSecret;

    /** 第三方平台配置，key 为平台名称（如 github/gitee） */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * 第三方平台配置
     */
    @Data
    public static class ProviderConfig {
        /** 客户端 ID */
        private String clientId;
        /** 客户端密钥 */
        private String clientSecret;
        /** 回调地址 */
        private String redirectUri;
        /** 授权地址（可选，使用平台默认地址） */
        private String authorizationUrl;
        /** Token 地址（可选，使用平台默认地址） */
        private String tokenUrl;
        /** 用户信息地址（可选，使用平台默认地址） */
        private String userInfoUrl;
        /** 授权范围 */
        private String scope;
        /** 额外参数，用于 URL 模板占位符替换（如 microsoft 的 tenant_id） */
        private Map<String, String> extra;
    }
}
