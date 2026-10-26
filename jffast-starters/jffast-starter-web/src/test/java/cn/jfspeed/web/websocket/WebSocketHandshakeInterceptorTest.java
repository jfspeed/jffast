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

import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 握手拦截器测试：子协议回显与 token 解析。
 *
 * <p>锁定修复：客户端声明 {@code jffast-token.<token>} 子协议时，服务端必须在响应头回显该子协议，
 * 否则浏览器按 WHATWG 规范判定握手失败，前端降级 {@code ?token=} 导致 token 落入 URL/访问日志。
 *
 * @author jffast
 */
@DisplayName("WebSocketHandshakeInterceptor 握手认证测试")
class WebSocketHandshakeInterceptorTest {

    private static final String TOKEN = "test-token-value";

    private ServletServerHttpRequest request(MockHttpServletRequest req) {
        return new ServletServerHttpRequest(req);
    }

    private ServletServerHttpResponse response(MockHttpServletResponse resp) {
        return new ServletServerHttpResponse(resp);
    }

    @Test
    @DisplayName("子协议握手：认证通过且回显 Sec-WebSocket-Protocol")
    void shouldEchoTokenSubprotocol() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        String encoded = URLEncoder.encode(TOKEN, StandardCharsets.UTF_8);
        String subprotocol = "jffast-token." + encoded;
        req.addHeader("Sec-WebSocket-Protocol", subprotocol);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken(TOKEN)).thenReturn(1001L);

            boolean ok = interceptor.beforeHandshake(request(req), response(resp), null, new HashMap<>());

            assertTrue(ok);
            assertEquals(subprotocol, resp.getHeader("Sec-WebSocket-Protocol"));
        }
    }

    @Test
    @DisplayName("URL 参数握手：认证通过且不写子协议响应头")
    void shouldNotEchoSubprotocolForQueryToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setParameter("token", TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken(TOKEN)).thenReturn(1001L);

            boolean ok = interceptor.beforeHandshake(request(req), response(resp), null, new HashMap<>());

            assertTrue(ok);
            assertNull(resp.getHeader("Sec-WebSocket-Protocol"));
        }
    }

    @Test
    @DisplayName("无效 token：握手被拒绝")
    void shouldRejectInvalidToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Sec-WebSocket-Protocol", "jffast-token." + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken(TOKEN)).thenReturn(null);

            boolean ok = interceptor.beforeHandshake(request(req), response(resp), null, new HashMap<>());

            assertFalse(ok);
            assertNull(resp.getHeader("Sec-WebSocket-Protocol"));
        }
    }

    @Test
    @DisplayName("无任何 token：握手被拒绝")
    void shouldRejectWhenNoToken() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse resp = new MockHttpServletResponse();

        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();
        boolean ok = interceptor.beforeHandshake(request(req), response(resp), null, new HashMap<>());

        assertFalse(ok);
    }

    @Test
    @DisplayName("多个子协议时取 jffast-token 前缀的那个并回显")
    void shouldPickTokenSubprotocolAmongMultiple() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        String encoded = URLEncoder.encode(TOKEN, StandardCharsets.UTF_8);
        String subprotocol = "jffast-token." + encoded;
        req.addHeader("Sec-WebSocket-Protocol", "chat, " + subprotocol);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        WebSocketHandshakeInterceptor interceptor = new WebSocketHandshakeInterceptor();
        try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.getLoginIdByToken(TOKEN)).thenReturn(1001L);

            boolean ok = interceptor.beforeHandshake(request(req), response(resp), null, new HashMap<>());

            assertTrue(ok);
            assertEquals(subprotocol, resp.getHeader("Sec-WebSocket-Protocol"));
        }
    }
}
