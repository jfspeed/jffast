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
import cn.jfspeed.web.websocket.WebSocketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket NONE 环境回归测试。
 *
 * <p>根因：{@code serverEndpointExporter} 曾无条件创建，{@code @SpringBootTest(webEnvironment = NONE)}
 * 下因缺 {@code ServerContainer} 导致整个上下文启动失败。本测试锁定修复：
 * NONE（ApplicationContextRunner 本身即非 Web 环境）下 service/interceptor 仍可注入，exporter 不建。
 *
 * @author jffast
 */
@DisplayName("WebSocketAutoConfiguration NONE 环境回归测试")
class WebSocketAutoConfigurationNoneTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebSocketAutoConfiguration.class));

    @Test
    @DisplayName("NONE 下上下文可启动：service 可注入、exporter 缺席")
    void noneContextStartsWithoutExporter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(WebSocketService.class);
            assertThat(context).hasSingleBean(WebSocketHandshakeInterceptor.class);
            assertThat(context).doesNotHaveBean(ServerEndpointExporter.class);
        });
    }

    @Test
    @DisplayName("关闭开关时整个 WS 配置退避")
    void disabledPropertyBacksOff() {
        runner.withPropertyValues("jffast.websocket.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WebSocketService.class);
                    assertThat(context).doesNotHaveBean(ServerEndpointExporter.class);
                });
    }
}
