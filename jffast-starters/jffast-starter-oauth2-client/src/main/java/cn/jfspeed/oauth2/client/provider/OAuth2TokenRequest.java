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

import lombok.Data;

/**
 * 换取令牌的请求参数（client_credentials 或 authorization_code 用）。
 *
 * @author jffast
 */
@Data
public class OAuth2TokenRequest {

    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String AUTHORIZATION_CODE = "authorization_code";

    /** 授权类型 */
    private String grantType;
    /** 授权码模式：上一步拿到的 code */
    private String code;
    /** 授权码模式：回调地址，须与申请时一致 */
    private String redirectUri;
}