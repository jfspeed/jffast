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


/**
 * GitHub 默认实现。接入地址走数据库配置；此处仅提供缺省 URL 模板兜底。
 *
 * @author jffast
 */
public class GithubOAuth2Provider extends AbstractOAuth2Provider {

    @Override
    public String getKey() {
        return "github";
    }

    @Override
    public String authorizeUrlTemplate() {
        return "https://github.com/login/oauth/authorize?client_id={client_id}&redirect_uri={redirect_uri}&scope={scope}&state={state}";
    }

    @Override
    public String tokenUrl() {
        return "https://github.com/login/oauth/access_token";
    }
}