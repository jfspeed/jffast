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
package cn.jfspeed.oauth2.client.provider;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientState;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.oauth2.client.entity.OAuth2ClientConfig;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 通用厂商实现基类：承载 authorize 引导 URL 拼装与令牌换取/解析的公共逻辑。
 * <p>
 * 各厂商子类只需提供默认 URL 模板与默认 scope；具体接入配置始终以数据库为准。
 *
 * @author jffast
 */
public abstract class AbstractOAuth2Provider implements OAuth2ClientProvider {

    @Override
    public String buildAuthorizeUrl(OAuth2ClientConfig cfg, OAuth2ClientState state) {
        String tpl = StringUtils.hasText(cfg.getAuthorizationUrl()) ? cfg.getAuthorizationUrl() : authorizeUrlTemplate();
        String redirect = redirectUri(cfg);
        String scope = StringUtils.hasText(cfg.getScopes()) ? cfg.getScopes() : defaultScope();

        Map<String, String> q = new LinkedHashMap<>();
        q.put("response_type", "code");
        q.put("client_id", cfg.getClientId());
        q.put("redirect_uri", redirect);
        if (StringUtils.hasText(scope)) {
            q.put("scope", scope);
        }
        q.put("state", state.encode());

        if (tpl.contains("{state}")) {
            // provider 自带完整 authorize 模板（如 github）：仅按序替换占位，不再追加 query，避免多余参数
            return tpl
                    .replace("{client_id}", url(cfg.getClientId()))
                    .replace("{redirect_uri}", url(redirect))
                    .replace("{scope}", url(scope))
                    .replace("{state}", url(state.encode()));
        }
        // 通用标准流程：authorizationUrl 来自数据库配置，拼装标准 OAuth2 鉴权地址
        return appendQuery(tpl, q);
    }

    @Override
    public OAuth2ClientToken exchangeToken(OAuth2ClientConfig cfg, OAuth2TokenRequest req) {
        String tokenUrl = StringUtils.hasText(cfg.getTokenUrl()) ? cfg.getTokenUrl() : tokenUrl();
        if (!StringUtils.hasText(tokenUrl)) {
            throw new BusinessException("provider[" + getKey() + "] 未配置 tokenUrl");
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", req.getGrantType());
        if (!"client_secret_basic".equalsIgnoreCase(cfg.getAuthMethod())) {
            form.put("client_id", cfg.getClientId());
            form.put("client_secret", cfg.getClientSecret());
        }
        if (OAuth2TokenRequest.CLIENT_CREDENTIALS.equals(req.getGrantType())) {
            String scope = StringUtils.hasText(cfg.getScopes()) ? cfg.getScopes() : defaultScope();
            if (StringUtils.hasText(scope)) {
                form.put("scope", scope);
            }
        } else if (OAuth2TokenRequest.AUTHORIZATION_CODE.equals(req.getGrantType())) {
            if (!StringUtils.hasText(req.getCode())) {
                throw new BusinessException("授权码模式缺少 code");
            }
            form.put("code", req.getCode());
            form.put("redirect_uri", StringUtils.hasText(req.getRedirectUri()) ? req.getRedirectUri() : redirectUri(cfg));
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if ("client_secret_basic".equalsIgnoreCase(cfg.getAuthMethod())) {
            String basic = Base64.getEncoder().encodeToString(
                    (cfg.getClientId() + ":" + cfg.getClientSecret()).getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + basic);
        }

        String body = OAuth2HttpUtil.postForm(tokenUrl, form, headers);
        return parseToken(body, cfg);
    }

    /** 解析令牌响应（JSON 或 form-urlencoded），时间字段用 epoch 毫秒存储 */
    protected OAuth2ClientToken parseToken(String body, OAuth2ClientConfig cfg) {
        Map<String, String> map = toMap(body);
        String access = map.get("access_token");
        if (!StringUtils.hasText(access)) {
            throw new BusinessException("OAuth2 令牌响应缺少 access_token: " + body);
        }
        OAuth2ClientToken token = new OAuth2ClientToken();
        token.setAccessToken(access);
        token.setTokenType(map.get("token_type"));
        token.setRefreshToken(map.get("refresh_token"));
        long now = System.currentTimeMillis();
        token.setIssuedAt(now);
        token.setAccessTokenExpiresIn(resolveExpiresIn(map, cfg));
        token.setExpiresAt(now + token.getAccessTokenExpiresIn() * 1000L);
        token.setRaw(body);
        return token;
    }

    /** 解析 expires_in：响应值非法（缺失/非数字/非正数）时回退配置 ttl，再回退默认 1 小时 */
    private long resolveExpiresIn(Map<String, String> map, OAuth2ClientConfig cfg) {
        String raw = map.get("expires_in");
        if (StringUtils.hasText(raw)) {
            try {
                long v = Long.parseLong(raw.trim());
                if (v > 0) {
                    return v;
                }
            } catch (NumberFormatException e) {
                // 非标准响应（如错误页/form 文本），按无 expires_in 处理走回退
            }
        }
        if (cfg.getTtl() != null && cfg.getTtl() > 0) {
            return cfg.getTtl();
        }
        return 3600L;
    }

    /** 将 JSON 或 form 响应体解析为扁平 Map */
    private Map<String, String> toMap(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (!StringUtils.hasText(body)) {
            return map;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            JSONObject json = JSONUtil.parseObj(trimmed);
            json.forEach((k, v) -> map.put(k, v == null ? "" : String.valueOf(v)));
        } else {
            for (String pair : trimmed.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    map.put(pair.substring(0, i), pair.substring(i + 1));
                }
            }
        }
        return map;
    }

    private String redirectUri(OAuth2ClientConfig cfg) {
        if (StringUtils.hasText(cfg.getRedirectUri())) {
            return cfg.getRedirectUri();
        }
        String tpl = redirectUriTemplate();
        if (StringUtils.hasText(tpl)) {
            return tpl.replace("{provider}", url(cfg.getProvider()));
        }
        return "";
    }

    private static String appendQuery(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(hasQuery ? "&" : "?");
            hasQuery = true;
            sb.append(url(e.getKey())).append('=').append(url(e.getValue()));
        }
        return sb.toString();
    }

    private static String url(String v) {
        return URLEncoder.encode(StringUtils.hasText(v) ? v : "", StandardCharsets.UTF_8).replace("+", "%20");
    }

    protected static <T> T orDefault(String val, Function<String, T> mapper, T def) {
        return StringUtils.hasText(val) ? mapper.apply(val) : def;
    }
}