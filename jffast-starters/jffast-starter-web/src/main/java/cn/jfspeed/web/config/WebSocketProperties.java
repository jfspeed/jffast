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

    /** 允许的来源（逗号分隔，空表示允许所有） */
    private String allowedOrigins = "";
}
