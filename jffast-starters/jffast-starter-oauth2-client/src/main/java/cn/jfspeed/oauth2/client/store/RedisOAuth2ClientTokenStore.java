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
package cn.jfspeed.oauth2.client.store;

import cn.hutool.json.JSONUtil;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.redis.util.RedisUtil;

/**
 * 默认令牌存储：复用系统内置 Redis。
 * <p>
 * 注意：系统 RedisTemplate 的 value 序列化器为 {@code JacksonJsonRedisSerializer}（Jackson 3），
 * 泛型读回会得到 Map，因此这里显式以 JSON 字符串存取，再用 Hutool 反序列化为
 * {@link OAuth2ClientToken}，保证类型安全。
 *
 * @author jffast
 */
public class RedisOAuth2ClientTokenStore implements OAuth2ClientTokenStore {

    private static final String PREFIX = "oauth2:client:token:";

    private String key(String provider) {
        return PREFIX + provider;
    }

    @Override
    public OAuth2ClientToken get(String provider) {
        String json = RedisUtil.get(key(provider));
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSONUtil.toBean(json, OAuth2ClientToken.class);
    }

    @Override
    public void put(String provider, OAuth2ClientToken token) {
        long ttl = token.remainingSeconds();
        RedisUtil.set(key(provider), JSONUtil.toJsonStr(token), ttl);
    }

    @Override
    public void evict(String provider) {
        RedisUtil.del(key(provider));
    }
}