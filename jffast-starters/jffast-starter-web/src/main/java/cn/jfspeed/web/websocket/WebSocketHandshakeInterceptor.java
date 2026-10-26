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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * WebSocket 握手拦截器：认证方式按以下顺序尝试。
 * <ol>
 *   <li><b>握手子协议</b> {@code Sec-WebSocket-Protocol: jffast-token.<urlencode(token)>}（推荐）——
 *       token **不出现在 URL** 里，不会被网关/代理的访问日志、浏览器历史记录留存；</li>
 *   <li><b>URL 参数</b> {@code ?token=xxx}（兼容旧前端 / 其它客户端）。</li>
 * </ol>
 *
 * <p>注意：客户端声明了子协议时，服务端**必须在响应头回显其中一个子协议**，否则浏览器按
 * WHATWG WebSocket 规范直接判定握手失败（前端会降级为 {@code ?token=} 把 token 塞进 URL）。
 * 因此这里把客户端的 {@code jffast-token.<...>} 原样回显（它由客户端生成、内容已知安全）。
 *
 * @author jffast
 */
@Slf4j
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    /** 子协议前缀（与前端 `shared/src/utils/websocket.ts` 的 TOKEN_SUBPROTOCOL 保持一致） */
    private static final String TOKEN_SUBPROTOCOL_PREFIX = "jffast-token.";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                ResolvedAuth auth = resolveAuth(servletRequest);
                if (auth.token() != null && !auth.token().isEmpty()) {
                    Object loginId = StpUtil.getLoginIdByToken(auth.token());
                    if (loginId != null) {
                        attributes.put("userId", Long.parseLong(loginId.toString()));
                        attributes.put("token", auth.token());
                        if (auth.subprotocol() != null && response instanceof ServletServerHttpResponse servletResponse) {
                            // 回显客户端请求的 token 子协议，浏览器才会完成子协议握手（token 不落 URL）。
                            // 必须直写 HttpServletResponse：ServletServerHttpResponse.getHeaders() 是缓冲写，
                            // 握手阶段不会刷新到真实响应，升级时就丢掉了。
                            servletResponse.getServletResponse().setHeader("Sec-WebSocket-Protocol", auth.subprotocol());
                        }
                        log.debug("WebSocket 握手认证成功，userId: {}", loginId);
                        return true;
                    }
                }
            }
            log.warn("WebSocket 握手失败：未提供有效 token");
            return false;
        } catch (Exception e) {
            log.warn("WebSocket 握手认证失败", e);
            return false;
        }
    }

    /**
     * 取 token：优先握手子协议（`Sec-WebSocket-Protocol`），其次 URL 参数。
     *
     * <p>子协议形如 `jffast-token.&lt;urlencode(token)&gt;` —— 前端做了 urlencode，因为子协议值里
     * 不允许出现逗号、空格等分隔符，而 Sa-Token 的 token 字符集不保证安全。
     */
    private ResolvedAuth resolveAuth(ServletServerHttpRequest request) {
        String header = request.getServletRequest().getHeader("Sec-WebSocket-Protocol");
        if (header != null && !header.isEmpty()) {
            for (String part : header.split(",")) {
                String candidate = part.trim();
                if (candidate.length() > TOKEN_SUBPROTOCOL_PREFIX.length()
                        && candidate.regionMatches(true, 0, TOKEN_SUBPROTOCOL_PREFIX, 0,
                                TOKEN_SUBPROTOCOL_PREFIX.length())) {
                    String raw = candidate.substring(TOKEN_SUBPROTOCOL_PREFIX.length());
                    return new ResolvedAuth(URLDecoder.decode(raw, StandardCharsets.UTF_8), candidate);
                }
            }
        }
        return new ResolvedAuth(request.getServletRequest().getParameter("token"), null);
    }

    /** 握手认证结果：token 及需要回显的子协议（query 兜底方式无子协议可回显） */
    private record ResolvedAuth(String token, String subprotocol) {
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后无需处理
    }
}
