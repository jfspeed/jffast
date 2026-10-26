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

import cn.jfspeed.oauth2.client.domain.OAuth2ClientState;
import cn.jfspeed.oauth2.client.domain.OAuth2ClientToken;
import cn.jfspeed.oauth2.client.entity.OAuth2ClientConfig;

/**
 * OAuth2 厂商实现抽象。业务方声明一个 {@link OAuth2ClientProvider} Bean 即可扩展新厂商；
 * 内置 github / gitee / microsoft，另有一个配置驱动的 {@link GenericOAuth2Provider} 兜底。
 * <p>
 * 存数据库的 {@link OAuth2ClientConfig#provider} 作为 service 定位 provider 的唯一键。
 *
 * @author jffast
 */
public interface OAuth2ClientProvider {

    /** 厂商唯一标识（与 sys_oauth2_client_config.provider 对应），如 github / generic */
    String getKey();

    /** 同 key 冲突时的优先级，越小越靠前 */
    default int getOrder() {
        return 100;
    }

    /**
     * 默认授权地址模板，支持占位：{client_id}/{redirect_uri}/{state}/{scope}。
     * 为空则直接使用配置里的 authorizationUrl。
     */
    default String authorizeUrlTemplate() {
        return "";
    }

    /** 默认令牌地址；为空则使用配置里的 tokenUrl */
    default String tokenUrl() {
        return "";
    }

    /** 默认回调模板，支持占位：{provider}；为空则使用配置 redirectUri */
    default String redirectUriTemplate() {
        return "";
    }

    /** 默认 scope（空格分隔）；为空则使用配置 scopes */
    default String defaultScope() {
        return "";
    }

    /** 生成 authorization_code 引导 URL */
    String buildAuthorizeUrl(OAuth2ClientConfig cfg, OAuth2ClientState state);

    /** 携带授权/客户端信息换取令牌 */
    OAuth2ClientToken exchangeToken(OAuth2ClientConfig cfg, OAuth2TokenRequest req);
}