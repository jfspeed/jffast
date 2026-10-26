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

import cn.jfspeed.web.websocket.WebSocketHandshakeInterceptor;
import cn.jfspeed.web.websocket.WebSocketEventHandler;
import cn.jfspeed.web.websocket.WebSocketService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket 自动配置
 *
 * @author jffast
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ServerEndpointExporter.class})
@ConditionalOnProperty(prefix = "jffast.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
public class WebSocketAutoConfiguration implements WebSocketConfigurer {

    private final WebSocketProperties properties;
    private final List<WebSocketEventHandler> handlers;

    public WebSocketAutoConfiguration(WebSocketProperties properties, List<WebSocketEventHandler> handlers) {
        this.properties = properties;
        this.handlers = handlers;
    }

    @Bean
    public WebSocketHandshakeInterceptor webSocketHandshakeInterceptor() {
        return new WebSocketHandshakeInterceptor();
    }

    @Bean
    public WebSocketService webSocketService() {
        return new WebSocketService(handlers);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] allowedOrigins = properties.getAllowedOrigins().isEmpty()
                ? new String[]{"*"}
                : properties.getAllowedOrigins().split(",");

        registry.addHandler(webSocketService(), properties.getPath())
                .addInterceptors(webSocketHandshakeInterceptor())
                .setAllowedOrigins(allowedOrigins);

        log.info("WebSocket 配置完成，路径: {}", properties.getPath());
    }
}
