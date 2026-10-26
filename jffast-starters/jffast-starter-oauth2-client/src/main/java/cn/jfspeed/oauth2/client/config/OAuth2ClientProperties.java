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
package cn.jfspeed.oauth2.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth2 Client Starter 配置属性（仅开关；第三方接入配置存数据库 sys_oauth2_client_config 表，走页面维护）。
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.oauth2.client")
public class OAuth2ClientProperties {

    /** 是否启用本 Starter，默认 false */
    private boolean enabled = false;

    /** 是否暴露调试令牌接口 /oauth2/client/token/{provider}，默认 false */
    private boolean exposeToken = false;
}
