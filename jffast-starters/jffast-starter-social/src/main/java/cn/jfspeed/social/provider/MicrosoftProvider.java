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
package cn.jfspeed.social.provider;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.social.config.SocialProperties;
import cn.jfspeed.social.domain.SocialUser;
import cn.jfspeed.social.util.SocialHttpUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author jffast
 */
public class MicrosoftProvider implements SocialLoginProvider {

    private static final String AUTH_URL_TEMPLATE = "https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/authorize";
    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/{tenant_id}/oauth2/v2.0/token";
    private static final String USER_INFO_URL = "https://graph.microsoft.com/v1.0/me";

    private final SocialProperties.ProviderConfig config;

    public MicrosoftProvider(SocialProperties.ProviderConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return "microsoft";
    }

    private String resolvePlaceholders(String url) {
        if (config.getExtra() != null) {
            for (Map.Entry<String, String> entry : config.getExtra().entrySet()) {
                url = url.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return url;
    }

    @Override
    public String getAuthorizationUrl(String state) {
        String authUrl = config.getAuthorizationUrl() != null
                ? resolvePlaceholders(config.getAuthorizationUrl())
                : resolvePlaceholders(AUTH_URL_TEMPLATE);
        String scope = URLEncoder.encode(
                config.getScope() != null ? config.getScope() : "openid profile email",
                StandardCharsets.UTF_8);
        return authUrl
                + "?client_id=" + config.getClientId()
                + "&response_type=code"
                + "&scope=" + scope
                + "&redirect_uri=" + URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    @Override
    public String getAccessToken(String code) {
        String tokenUrl = config.getTokenUrl() != null
                ? resolvePlaceholders(config.getTokenUrl())
                : resolvePlaceholders(TOKEN_URL_TEMPLATE);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("client_secret", config.getClientSecret());
        params.put("code", code);
        params.put("redirect_uri", config.getRedirectUri());
        params.put("grant_type", "authorization_code");
        params.put("scope", config.getScope() != null ? config.getScope() : "openid profile email");

        String response = SocialHttpUtil.postForm(tokenUrl, params);
        JSONObject json = JSONUtil.parseObj(response);
        return json.getStr("access_token");
    }

    @Override
    public SocialUser getUserInfo(String accessToken) {
        String userInfoUrl = config.getUserInfoUrl() != null ? config.getUserInfoUrl() : USER_INFO_URL;
        String response = SocialHttpUtil.getWithAuth(userInfoUrl, accessToken);
        JSONObject json = JSONUtil.parseObj(response);

        SocialUser user = new SocialUser();
        user.setProvider("microsoft");
        user.setOpenId(json.getStr("id"));
        user.setUsername(json.getStr("userPrincipalName"));
        user.setNickname(json.getStr("displayName"));
        user.setEmail(json.getStr("mail") != null ? json.getStr("mail") : json.getStr("userPrincipalName"));
        user.setRawUserInfo(response);
        return user;
    }
}
