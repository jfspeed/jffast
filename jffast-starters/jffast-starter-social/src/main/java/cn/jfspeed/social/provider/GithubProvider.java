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
public class GithubProvider implements SocialLoginProvider {

    private static final String AUTH_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_INFO_URL = "https://api.github.com/user";

    private final SocialProperties.ProviderConfig config;

    public GithubProvider(SocialProperties.ProviderConfig config) {
        this.config = config;
    }

    @Override
    public String getProviderName() {
        return "github";
    }

    @Override
    public String getAuthorizationUrl(String state) {
        String authUrl = config.getAuthorizationUrl() != null ? config.getAuthorizationUrl() : AUTH_URL;
        return authUrl
                + "?client_id=" + config.getClientId()
                + "&redirect_uri=" + URLEncoder.encode(config.getRedirectUri(), StandardCharsets.UTF_8)
                + "&scope=" + (config.getScope() != null ? config.getScope() : "user:email")
                + "&state=" + state;
    }

    @Override
    public String getAccessToken(String code) {
        String tokenUrl = config.getTokenUrl() != null ? config.getTokenUrl() : TOKEN_URL;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.getClientId());
        params.put("client_secret", config.getClientSecret());
        params.put("code", code);
        params.put("redirect_uri", config.getRedirectUri());

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
        user.setProvider("github");
        user.setOpenId(json.getStr("id"));
        user.setUsername(json.getStr("login"));
        user.setNickname(json.getStr("name"));
        user.setAvatarUrl(json.getStr("avatar_url"));
        user.setEmail(json.getStr("email"));
        user.setRawUserInfo(response);
        return user;
    }
}
