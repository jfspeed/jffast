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
package cn.jfspeed.oauth2.client.service;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientState;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.oauth2.client.entity.OAuth2ClientConfig;
import cn.jfspeed.oauth2.client.provider.GenericOAuth2Provider;
import cn.jfspeed.oauth2.client.provider.OAuth2ClientProvider;
import cn.jfspeed.oauth2.client.provider.OAuth2TokenRequest;
import cn.jfspeed.oauth2.client.store.OAuth2ClientTokenStore;
import cn.jfspeed.redis.util.RedisUtil;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * OAuth2 客户端核心服务：令牌获取/缓存/防并发/过期刷新，以及 authorization_code 的
 * authorize 引导与 callback 换发。令牌缓存默认复用系统 Redis。
 * <p>
 * authorization_code 的 state 防 CSRF：发起授权时将 nonce 存入 Redis（一次性、限时），
 * 回调时必须命中且 provider 一致，校验通过即删除，伪造/重放的 state 均被拒绝。
 *
 * @author jffast
 */
public class OAuth2ClientService {

    /** state nonce 在 Redis 中的 key 前缀 */
    private static final String STATE_KEY_PREFIX = "oauth2:client:state:";

    /** state 有效期（秒）：覆盖用户在第三方页面完成授权的常规耗时 */
    private static final long STATE_TTL_SECONDS = 600L;

    private final OAuth2ClientTokenStore store;
    private final OAuth2ClientConfigService configService;
    private final Map<String, OAuth2ClientProvider> providers;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public OAuth2ClientService(OAuth2ClientTokenStore store,
                               OAuth2ClientConfigService configService,
                               List<OAuth2ClientProvider> providers) {
        this.store = store;
        this.configService = configService;
        this.providers = providers.stream()
                .collect(Collectors.toMap(OAuth2ClientProvider::getKey,
                        Function.identity(), (a, b) -> a.getOrder() <= b.getOrder() ? a : b));
    }

    /**
     * 换取或复用令牌（client_credentials 凭证模式）。
     * 缓存未过期直接返回；过期/不存在时在 per-provider 锁内重查并换发，避免并发冲峰。
     * 授权码模式走 {@link #buildAuthorizeUrl} + {@link #handleCallback} 流程，不走本方法。
     */
    public OAuth2ClientToken getToken(String provider) {
        OAuth2ClientProvider p = resolve(provider);
        OAuth2ClientToken cached = store.get(provider);
        if (cached != null && !cached.isExpired() && StringUtils.hasText(cached.getAccessToken())) {
            return cached;
        }
        synchronized (lock(provider)) {
            cached = store.get(provider);
            if (cached != null && !cached.isExpired() && StringUtils.hasText(cached.getAccessToken())) {
                return cached;
            }
            OAuth2ClientConfig cfg = configService.getEnabledByProvider(provider);
            OAuth2TokenRequest req = new OAuth2TokenRequest();
            req.setGrantType(OAuth2TokenRequest.CLIENT_CREDENTIALS);
            OAuth2ClientToken fresh = p.exchangeToken(cfg, req);
            store.put(provider, fresh);
            return fresh;
        }
    }

    /** 生成 authorization_code 引导 URL，并将 state nonce 存入 Redis 供回调校验（一次性） */
    public String buildAuthorizeUrl(String provider) {
        OAuth2ClientProvider p = resolve(provider);
        OAuth2ClientConfig cfg = configService.getEnabledByProvider(provider);
        OAuth2ClientState state = new OAuth2ClientState();
        state.setProvider(provider);
        state.setNonce(UUID.randomUUID().toString().replace("-", ""));
        RedisUtil.set(stateKey(state.getNonce()), provider, STATE_TTL_SECONDS);
        return p.buildAuthorizeUrl(cfg, state);
    }

    /** 授权码回调：校验 state（一次性防 CSRF）后用 code 换 token 并缓存 */
    public OAuth2ClientToken handleCallback(String provider, String code, String state) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(code)) {
            throw new BusinessException("回调缺少 provider/code");
        }
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("回调缺少 state");
        }
        OAuth2ClientState decoded;
        try {
            decoded = OAuth2ClientState.decode(state);
        } catch (Exception e) {
            throw new BusinessException("回调 state 非法");
        }
        if (!provider.equals(decoded.getProvider()) || !StringUtils.hasText(decoded.getNonce())) {
            throw new BusinessException("回调 provider 与 state 不一致");
        }
        String stateKey = stateKey(decoded.getNonce());
        Object cached = RedisUtil.get(stateKey);
        if (cached == null || !provider.equals(String.valueOf(cached))) {
            throw new BusinessException("state 已失效或非法，请重新发起授权");
        }
        // 一次性消费：先删后换发，防止重放
        RedisUtil.del(stateKey);

        OAuth2ClientProvider p = resolve(provider);
        OAuth2ClientConfig cfg = configService.getEnabledByProvider(provider);
        OAuth2TokenRequest req = new OAuth2TokenRequest();
        req.setGrantType(OAuth2TokenRequest.AUTHORIZATION_CODE);
        req.setCode(code);
        req.setRedirectUri(cfg.getRedirectUri());
        OAuth2ClientToken fresh = p.exchangeToken(cfg, req);
        store.put(provider, fresh);
        return fresh;
    }

    /** 手动清空某 provider 的缓存令牌（配置变更后调用） */
    public void evict(String provider) {
        store.evict(provider);
    }

    private OAuth2ClientProvider resolve(String provider) {
        OAuth2ClientProvider p = providers.get(provider);
        if (p == null) {
            // 未内置时兜底到 generic 配置驱动实现
            p = providers.get(GenericOAuth2Provider.GENERIC);
        }
        if (p == null) {
            throw new BusinessException("暂不支持的 provider=" + provider);
        }
        return p;
    }

    private Object lock(String provider) {
        return locks.computeIfAbsent(provider, k -> new Object());
    }

    private String stateKey(String nonce) {
        return STATE_KEY_PREFIX + nonce;
    }
}