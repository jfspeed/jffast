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
package cn.jfspeed.oauth2.client.domain;

import cn.hutool.json.JSONUtil;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth2 授权码模式 state（base64url JSON），用于回调与 token 换发时校验 provider 一致并防 CSRF。
 *
 * @author jffast
 */
@Data
public class OAuth2ClientState {

    private String provider;
    private String nonce;

    public String encode() {
        String json = JSONUtil.toJsonStr(this);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    public static OAuth2ClientState decode(String encoded) {
        String json = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        return JSONUtil.toBean(json, OAuth2ClientState.class);
    }
}
