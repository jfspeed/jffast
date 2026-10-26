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

import cn.jfspeed.social.domain.SocialUser;

/**
 * 社交登录平台适配（策略模式）。
 * <p>
 * 新增平台只需实现本接口并注册为 Bean，{@code SocialLoginService} 会按
 * {@link #getProviderName()} 自动收集。
 *
 * @author jffast
 */
public interface SocialLoginProvider {

    /** 平台标识（小写），如 github / gitee / microsoft / wecom / dingtalk */
    String getProviderName();

    /** 生成授权跳转地址 */
    String getAuthorizationUrl(String state);

    /** 用授权码换取访问令牌 */
    String getAccessToken(String code);

    /** 用访问令牌获取用户信息 */
    SocialUser getUserInfo(String accessToken);

    /**
     * 用「授权码 + 访问令牌」获取用户信息。
     * <p>
     * 默认实现等价于 {@link #getUserInfo(String)}；企业微信、钉钉等服务端令牌与用户授权码
     * 需要分别参与用户信息请求的平台可覆写本方法。
     *
     * @param code        回调返回的用户授权码
     * @param accessToken {@link #getAccessToken(String)} 返回的访问令牌
     */
    default SocialUser getUserInfoByCode(String code, String accessToken) {
        return getUserInfo(accessToken);
    }
}
