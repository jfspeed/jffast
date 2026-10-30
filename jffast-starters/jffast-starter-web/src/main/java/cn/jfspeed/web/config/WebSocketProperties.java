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
package cn.jfspeed.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket 配置属性
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.websocket")
public class WebSocketProperties {

    /** 是否启用 WebSocket */
    private boolean enabled = true;

    /** WebSocket 路径 */
    private String path = "/ws";

    /**
     * 允许的来源（逗号分隔）。
     *
     * <p>取值口径（2026-10-29 收紧，此前「空 = 允许任意来源」）：
     * <ul>
     *   <li><b>留空（默认）</b>：只放行<b>同源</b>握手，任何带 {@code Origin} 头的跨源握手返回 403。
     *       WebSocket 协议本身没有同源限制，浏览器也不像 CORS 那样先预检，
     *       「默认放行所有来源」等于任意站点都能拿着用户 token 建连接；</li>
     *   <li><b>显式列出</b>（如 {@code https://fast.jfspeed.cn,https://fast-demo.jfspeed.cn}）：放行这些来源；</li>
     *   <li><b>{@code *}</b>：放行任意来源（仅本地联调/压测用，生产须列出具体站点）。</li>
     * </ul>
     */
    private String allowedOrigins = "";
}
