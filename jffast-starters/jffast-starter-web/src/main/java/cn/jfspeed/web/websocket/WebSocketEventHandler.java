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
package cn.jfspeed.web.websocket;

import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 业务扩展点：由业务模块实现并以 Spring Bean 形式注册，
 * starter 的 {@link WebSocketService} 会在连接建立 / 断开 / 收到消息时回调。
 *
 * <p>所有方法均为默认空实现，业务方按需覆写即可；starter 不强制任何业务行为。</p>
 *
 * @author jffast
 */
public interface WebSocketEventHandler {

    /**
     * 连接建立后回调。
     *
     * @param service starter 的 WebSocketService（可用于 sendToUser / sendToAll 等）
     * @param session 当前会话
     * @param userId  握手阶段解析出的用户 ID（未登录为 null）
     */
    default void onOpen(WebSocketService service, WebSocketSession session, Long userId) {
    }

    /**
     * 连接断开后回调。
     */
    default void onClose(WebSocketService service, WebSocketSession session, Long userId) {
    }

    /**
     * 收到文本消息后回调（心跳 ping 已由 starter 内部处理，不会转发到这里）。
     */
    default void onMessage(WebSocketService service, WebSocketSession session, Long userId, String payload) {
    }
}
