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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IPUtil} 单元测试。
 * <p>
 * 重点覆盖 {@link IPUtil#getClientIp} 的「伪造免疫」：{@code X-Forwarded-For} 是客户端可控的，
 * 一旦实现退化成「取首跳」，下面第 4、5 条用例必须失败。
 *
 * @author jffast
 */
class IPUtilTest {

    /** 反向代理所在的内网 CIDR（模拟部署拓扑：后端只信任前置 nginx） */
    private static final List<String> TRUSTED = Collections.singletonList("172.16.0.0/12");

    private static HttpServletRequest request(String remoteAddr, String xForwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xForwardedFor);
        return request;
    }

    // ==================================================================
    // getClientIp
    // ==================================================================

    @Test
    @DisplayName("未配置可信代理时完全不读 X-Forwarded-For，直接返回 TCP 对端地址")
    void returnsRemoteAddrWhenNoTrustedProxyConfigured() {
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("203.0.113.9", "1.1.1.1"), Collections.emptyList()));
    }

    @Test
    @DisplayName("对端不是可信代理时，客户端伪造的 X-Forwarded-For 被忽略")
    void ignoresSpoofedHeaderWhenRemoteNotTrusted() {
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("203.0.113.9", "1.1.1.1"), TRUSTED));
    }

    @Test
    @DisplayName("对端是可信代理且只追加了一跳时，取该跳为真实客户端 IP")
    void takesAppendedHopWhenRemoteTrusted() {
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("172.16.0.5", "203.0.113.9"), TRUSTED));
    }

    @Test
    @DisplayName("客户端伪造首跳 + 代理追加真实地址时，取最右的非可信地址（伪造免疫）")
    void skipsSpoofedFirstHop() {
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("172.16.0.5", "1.1.1.1, 203.0.113.9"), TRUSTED));
    }

    @Test
    @DisplayName("多级代理时逐跳右移，跳过所有可信代理地址")
    void skipsMultipleTrustedHops() {
        // 链表：客户端声称 1.1.1.1 → 真实客户端 203.0.113.9 → 代理1 172.16.0.5 → 代理2 172.16.0.6
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("172.16.0.6", "1.1.1.1, 203.0.113.9, 172.16.0.5, 172.16.0.6"), TRUSTED));
    }

    @Test
    @DisplayName("X-Forwarded-For 全是可信代理时回退 TCP 对端地址")
    void fallsBackToRemoteWhenAllHopsTrusted() {
        assertEquals("172.16.0.5",
                IPUtil.getClientIp(request("172.16.0.5", "172.16.0.3, 172.16.0.4"), TRUSTED));
    }

    @Test
    @DisplayName("可信代理未转发 X-Forwarded-For 时回退 TCP 对端地址")
    void fallsBackToRemoteWhenHeaderMissing() {
        assertEquals("172.16.0.5", IPUtil.getClientIp(request("172.16.0.5", null), TRUSTED));
    }

    @Test
    @DisplayName("多个可信 CIDR 取并集")
    void matchesAnyOfMultipleTrustedCidrs() {
        List<String> trusted = Arrays.asList("10.0.0.0/8", "172.16.0.0/12");
        assertEquals("203.0.113.9",
                IPUtil.getClientIp(request("10.1.2.3", "203.0.113.9"), trusted));
    }

    // ==================================================================
    // matchesCidr
    // ==================================================================

    @Test
    @DisplayName("IPv4 CIDR 命中与不命中")
    void matchesIpv4Cidr() {
        assertTrue(IPUtil.matchesCidr("172.16.5.9", "172.16.0.0/12"));
        assertTrue(IPUtil.matchesCidr("192.168.1.200", "192.168.1.0/24"));
        assertFalse(IPUtil.matchesCidr("172.32.0.1", "172.16.0.0/12"));
        assertFalse(IPUtil.matchesCidr("192.168.2.1", "192.168.1.0/24"));
    }

    @Test
    @DisplayName("非 8 整数倍前缀按位掩码比较")
    void matchesNonByteAlignedPrefix() {
        assertTrue(IPUtil.matchesCidr("192.168.1.129", "192.168.1.128/25"));
        assertFalse(IPUtil.matchesCidr("192.168.1.127", "192.168.1.128/25"));
    }

    @Test
    @DisplayName("裸 IP 视为 /32 精确匹配")
    void treatsBareIpAsExactMatch() {
        assertTrue(IPUtil.matchesCidr("127.0.0.1", "127.0.0.1"));
        assertFalse(IPUtil.matchesCidr("127.0.0.2", "127.0.0.1"));
    }

    @Test
    @DisplayName("/0 匹配同协议族任意地址")
    void matchesAnyAddressWithZeroPrefix() {
        assertTrue(IPUtil.matchesCidr("8.8.8.8", "0.0.0.0/0"));
    }

    @Test
    @DisplayName("IPv6 CIDR 命中与不命中")
    void matchesIpv6Cidr() {
        assertTrue(IPUtil.matchesCidr("2001:db8::1", "2001:db8::/32"));
        assertFalse(IPUtil.matchesCidr("2001:db9::1", "2001:db8::/32"));
    }

    @Test
    @DisplayName("协议族不同直接判否（IPv4 地址不会命中 IPv6 CIDR）")
    void rejectsCrossProtocolComparison() {
        assertFalse(IPUtil.matchesCidr("127.0.0.1", "::1"));
        assertFalse(IPUtil.matchesCidr("::1", "127.0.0.0/8"));
    }

    @Test
    @DisplayName("非法输入一律判否，且不会触发 DNS 解析")
    void rejectsMalformedInput() {
        assertFalse(IPUtil.matchesCidr("203.0.113.9", "not-an-ip"));
        assertFalse(IPUtil.matchesCidr("203.0.113.9", "203.0.113.0/99"));
        assertFalse(IPUtil.matchesCidr("203.0.113.9", "203.0.113.0/"));
        assertFalse(IPUtil.matchesCidr("203.0.113", "203.0.113.0/24"));
        assertFalse(IPUtil.matchesCidr("999.1.1.1", "999.1.1.0/24"));
        // 形如 host:port 或域名的输入必须判否（否则会被当成主机名做 DNS 解析）
        assertFalse(IPUtil.matchesCidr("203.0.113.9", "example.com:80"));
        assertFalse(IPUtil.matchesCidr(null, "172.16.0.0/12"));
        assertFalse(IPUtil.matchesCidr("172.16.0.1", null));
    }
}
