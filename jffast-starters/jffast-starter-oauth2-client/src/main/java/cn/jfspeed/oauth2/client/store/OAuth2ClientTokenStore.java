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

import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;

/**
 * OAuth2 令牌存储抽象。默认实现复用系统内置 Redis（RedisUtil），
 * 业务方也可替换为自定义实现。
 *
 * @author jffast
 */
public interface OAuth2ClientTokenStore {

    OAuth2ClientToken get(String provider);

    void put(String provider, OAuth2ClientToken token);

    void evict(String provider);
}
