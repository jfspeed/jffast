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
package cn.jfspeed.common.web.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ClientIpResolver} 单元测试。
 * <p>
 * 算法本身由 {@link IPUtilTest} 覆盖，这里只盯住「包装层不要把安全默认值丢掉」：
 * 代理列表为 null 时必须等价于「不信任任何转发头」，而不是抛异常或退回取首跳。
 *
 * @author jffast
 */
class ClientIpResolverTest {

    private static HttpServletRequest request(String remoteAddr, String xForwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        return request;
    }

    @Test
    @DisplayName("按配置的可信代理回溯，取最右侧的非可信跳")
    void resolvesThroughTrustedProxies() {
        ClientIpResolver resolver = new ClientIpResolver(Collections.singletonList("172.16.0.0/12"));

        assertEquals("203.0.113.9",
                resolver.resolve(request("172.16.0.5", "1.1.1.1, 203.0.113.9")));
    }

    @Test
    @DisplayName("代理列表为 null 时退化为「只认 TCP 对端地址」，不抛异常也不读转发头")
    void nullProxiesMeansNoForwardedHeaderTrust() {
        ClientIpResolver resolver = new ClientIpResolver(null);

        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "1.1.1.1")));
    }

    @Test
    @DisplayName("空代理列表同样不信任转发头")
    void emptyProxiesMeansNoForwardedHeaderTrust() {
        ClientIpResolver resolver = new ClientIpResolver(List.of());

        assertEquals("203.0.113.9", resolver.resolve(request("203.0.113.9", "1.1.1.1")));
    }
}
