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

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 服务
 * 管理 WebSocket 连接和消息推送，并回调业务扩展点 {@link WebSocketEventHandler}。
 *
 * @author jffast
 */
@Slf4j
public class WebSocketService extends TextWebSocketHandler {

    /**
     * 用户 ID -> WebSocket 会话
     */
    private static final Map<Long, Set<WebSocketSession>> USER_SESSIONS = new ConcurrentHashMap<>();

    /**
     * WebSocket 会话 -> 用户 ID
     */
    private static final Map<WebSocketSession, Long> SESSION_USER_MAP = new ConcurrentHashMap<>();

    /** 业务扩展点（业务模块以 Spring Bean 注册，无则空列表） */
    private final List<WebSocketEventHandler> handlers;

    public WebSocketService(List<WebSocketEventHandler> handlers) {
        this.handlers = handlers == null ? List.of() : handlers;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object userIdObj = session.getAttributes().get("userId");
        Long userId = userIdObj instanceof Long u ? u : null;
        if (userId != null) {
            USER_SESSIONS.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
            SESSION_USER_MAP.put(session, userId);
            log.debug("WebSocket 连接建立，userId: {}", userId);
        }
        for (WebSocketEventHandler handler : handlers) {
            handler.onOpen(this, session, userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = SESSION_USER_MAP.remove(session);
        if (userId != null) {
            Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    USER_SESSIONS.remove(userId);
                }
            }
            log.debug("WebSocket 连接关闭，userId: {}", userId);
        }
        for (WebSocketEventHandler handler : handlers) {
            handler.onClose(this, session, userId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 处理客户端心跳消息
        String payload = message.getPayload();
        if ("ping".equals(payload)) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException e) {
                log.error("发送心跳响应失败", e);
            }
            return;
        }
        Long userId = SESSION_USER_MAP.get(session);
        for (WebSocketEventHandler handler : handlers) {
            handler.onMessage(this, session, userId, payload);
        }
    }

    /**
     * 发送消息给所有连接的用户
     */
    public void sendToAll(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (Set<WebSocketSession> sessions : USER_SESSIONS.values()) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("发送消息失败，sessionId: {}", session.getId(), e);
                    }
                }
            }
        }
    }

    /**
     * 发送消息给指定用户
     */
    public void sendToUser(Long userId, String message) {
        Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
        if (sessions != null) {
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("发送消息失败，userId: {}, sessionId: {}", userId, session.getId(), e);
                    }
                }
            }
        }
    }

    /**
     * 发送消息给指定用户集合
     */
    public void sendToUsers(Set<Long> userIds, String message) {
        TextMessage textMessage = new TextMessage(message);
        for (Long userId : userIds) {
            Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
            if (sessions != null) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.error("发送消息失败，userId: {}, sessionId: {}", userId, session.getId(), e);
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineCount() {
        return USER_SESSIONS.size();
    }

    /**
     * 获取指定用户是否在线
     */
    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = USER_SESSIONS.get(userId);
        return sessions != null && !sessions.isEmpty();
    }
}
