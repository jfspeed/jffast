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

/**
 * @author jffast
 * @date 2024/1/15 10:15
 * @desc
 */

import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;

@Slf4j

public class IPUtil {
    private IPUtil() {
    }

    /**
     * 取「请求声明的」客户端 IP：优先 {@code X-Forwarded-For} 的首跳，取不到再看其它代理头，最后回退 TCP 对端地址。
     * <p>
     * <b>【安全警告】本方法不可用于任何安全判定</b>（限流、IP 白名单、风控、审计定责）。
     * 原因：{@code X-Forwarded-For} 是请求头，**客户端可以任意伪造**；而常见反向代理配置
     * （本项目自带的 nginx 模板即 {@code proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;}）
     * 是在客户端原有值后面**追加**真实地址，于是「客户端伪造值 + 代理追加值」形成的链表里，
     * 首跳恰好是伪造值 —— 谁按首跳做判定，谁就被 100% 绕过。
     * <p>
     * 本方法只适合「尽力而为」的展示与日志场景。需要真实客户端 IP 的安全场景一律用
     * {@link #getClientIp(HttpServletRequest, Collection)}。
     *
     * @param request 当前请求
     * @return 声明的客户端 IP
     */
    public static String getIpAddress(HttpServletRequest request) {
        String ip = null;
        String ipAddresses = request.getHeader("X-Forwarded-For");
        String unknown = "unknown";
        if (ipAddresses == null || ipAddresses.length() == 0 || unknown.equalsIgnoreCase(ipAddresses)) {
            ipAddresses = request.getHeader("Proxy-Client-IP");
        }

        if (ipAddresses == null || ipAddresses.length() == 0 || unknown.equalsIgnoreCase(ipAddresses)) {
            ipAddresses = request.getHeader("WL-Proxy-Client-IP");
        }

        if (ipAddresses == null || ipAddresses.length() == 0 || unknown.equalsIgnoreCase(ipAddresses)) {
            ipAddresses = request.getHeader("HTTP_CLIENT_IP");
        }

        if (ipAddresses == null || ipAddresses.length() == 0 || unknown.equalsIgnoreCase(ipAddresses)) {
            ipAddresses = request.getHeader("X-Real-IP");
        }

        if (ipAddresses != null && ipAddresses.length() != 0) {
            ip = ipAddresses.split(",")[0];
        }

        if (ip == null || ip.length() == 0 || unknown.equalsIgnoreCase(ipAddresses)) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

    /**
     * 解析真实客户端 IP（<b>安全场景专用</b>：限流 / IP 白名单 / 风控）。
     * <p>
     * 判定顺序：
     * <ol>
     *   <li>TCP 对端地址（{@code request.getRemoteAddr()}）不命中 {@code trustedProxyCidrs}
     *       ⇒ 直接返回对端地址，**完全不读转发头**。攻击者自己发 {@code X-Forwarded-For} 无效，
     *       因为直连场景下他不可能是「可信代理」。</li>
     *   <li>对端地址命中可信代理 ⇒ 从 {@code X-Forwarded-For} 链表**最右侧**向左回溯，
     *       跳过所有本身也是可信代理的地址，第一个非可信地址即真实客户端。
     *       按「追加」语义，代理追加的永远是链表尾部，客户端伪造值只会更靠左，因此从右往左取天然免疫伪造。</li>
     *   <li>整条链表都是可信代理（或链表为空）⇒ 回退对端地址。</li>
     * </ol>
     * 相比「取首跳」或「固定跳数」，本算法不需要运维填「有几层代理」，也就不存在
     * 「填错层数 → 取到伪造值」的失效模式。
     *
     * @param request           当前请求
     * @param trustedProxyCidrs 可信反向代理的 IP / CIDR 列表；<b>为空表示不信任任何转发头</b>（安全默认）
     * @return 真实客户端 IP；极端情况下返回 TCP 对端地址，不会返回 null
     */
    public static String getClientIp(HttpServletRequest request, Collection<String> trustedProxyCidrs) {
        String remote = request == null ? null : request.getRemoteAddr();
        if (remote == null) {
            remote = "";
        }
        if (trustedProxyCidrs == null || trustedProxyCidrs.isEmpty() || !matchesAny(remote, trustedProxyCidrs)) {
            return remote;
        }
        List<String> hops = forwardedForHops(request);
        for (int i = hops.size() - 1; i >= 0; i--) {
            String hop = hops.get(i);
            if (!matchesAny(hop, trustedProxyCidrs)) {
                return hop;
            }
        }
        return remote;
    }

    /**
     * 判断 IP 是否落在给定 CIDR（或精确 IP）内，支持 IPv4 / IPv6。
     * <p>
     * 只接受**地址字面量**：非字面量直接判否，绝不交给 {@code InetAddress.getByName} 触发 DNS 解析
     * （{@code X-Forwarded-For} 的内容是攻击者可控的，一旦走 DNS 就成了可被滥用的解析放大点）。
     *
     * @param ip   待判定 IP，如 {@code 203.0.113.9} / {@code 2001:db8::1}
     * @param cidr CIDR 或裸 IP，如 {@code 172.16.0.0/12} / {@code 127.0.0.1} / {@code 2001:db8::/32}
     * @return 命中返回 true
     */
    public static boolean matchesCidr(String ip, String cidr) {
        if (ip == null || cidr == null) {
            return false;
        }
        String ipText = ip.trim();
        String cidrText = cidr.trim();
        if (ipText.isEmpty() || cidrText.isEmpty()) {
            return false;
        }
        int slash = cidrText.indexOf('/');
        String base = slash < 0 ? cidrText : cidrText.substring(0, slash);
        int prefixBits = slash < 0 ? -1 : parsePrefixLength(cidrText.substring(slash + 1));
        if (slash >= 0 && prefixBits < 0) {
            return false;
        }

        byte[] ipBytes = addressBytes(ipText);
        byte[] baseBytes = addressBytes(base);
        if (ipBytes == null || baseBytes == null || ipBytes.length != baseBytes.length) {
            // 长度不等即协议族不同（IPv4 的 4 字节 vs IPv6 的 16 字节），直接判否
            return false;
        }
        if (prefixBits < 0) {
            prefixBits = ipBytes.length * 8;
        }
        if (prefixBits > ipBytes.length * 8) {
            return false;
        }
        int fullBytes = prefixBits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (ipBytes[i] != baseBytes[i]) {
                return false;
            }
        }
        int remainingBits = prefixBits % 8;
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (ipBytes[fullBytes] & mask) == (baseBytes[fullBytes] & mask);
    }

    private static boolean matchesAny(String ip, Collection<String> cidrs) {
        for (String cidr : cidrs) {
            if (matchesCidr(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拆 {@code X-Forwarded-For}：按逗号切分并去掉空白与空串。
     * 代理按「追加」语义写入，因此链表顺序是「最左=最外层客户端声明，最右=最近一跳追加」。
     */
    private static List<String> forwardedForHops(HttpServletRequest request) {
        List<String> hops = new ArrayList<>();
        if (request == null) {
            return hops;
        }
        String header = request.getHeader("X-Forwarded-For");
        if (header == null || header.isEmpty()) {
            return hops;
        }
        for (String part : header.split(",")) {
            String hop = part.trim();
            if (!hop.isEmpty()) {
                hops.add(hop);
            }
        }
        return hops;
    }

    private static int parsePrefixLength(String text) {
        if (text.isEmpty() || text.length() > 3) {
            return -1;
        }
        int value = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value > 128 ? -1 : value;
    }

    /**
     * 把 IP 字面量转成字节数组；非字面量返回 null（不触发 DNS）。
     */
    private static byte[] addressBytes(String value) {
        if (value.indexOf(':') >= 0) {
            String text = value;
            int zone = text.indexOf('%');
            if (zone >= 0) {
                text = text.substring(0, zone);
            }
            if (text.length() > 1 && text.charAt(0) == '[' && text.charAt(text.length() - 1) == ']') {
                text = text.substring(1, text.length() - 1);
            }
            // 只允许十六进制与 ':' '.'，挡掉 "host:port"、域名等会触发 DNS 的输入
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                boolean hexOrSep = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                        || c == ':' || c == '.';
                if (!hexOrSep) {
                    return null;
                }
            }
            try {
                return InetAddress.getByName(text).getAddress();
            } catch (Exception e) {
                return null;
            }
        }
        // IPv4 手工解析，彻底避开 getByName
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int octet = 0;
            for (int k = 0; k < part.length(); k++) {
                char c = part.charAt(k);
                if (c < '0' || c > '9') {
                    return null;
                }
                octet = octet * 10 + (c - '0');
            }
            if (octet > 255) {
                return null;
            }
            bytes[i] = (byte) octet;
        }
        return bytes;
    }

    /**
     * 获取本机有效IPv4地址（非回环地址、非虚拟机地址*
     * @return 有效IPv4地址，若未找到则返回null
     */
    public static String getLocalIp() {
        try {
            // 遍历所有网络接*
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                // 过滤*27.0.0.1和已关闭的接*
                if (ni.isLoopback() || !ni.isUp()) {
                    continue;
                }

                // 遍历接口下的所有IP地址
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 只保留IPv4地址，且不是回环地址
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 可根据需求添加更多过滤条件（如排除虚拟机IP*
                        if (!isVirtualMachineIp(ip)) {
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Operation failed", e);
        }
        return null;
    }

    /**
     * 判断是否为常见虚拟机IP（可根据实际环境调整*
     * 虚拟机IP通常在特定网段（如VMware*92.168.150.x，VirtualBox*92.168.56.x等）
     */
    private static boolean isVirtualMachineIp(String ip) {
        // 示例：排除VMware和VirtualBox常见网段
        return ip.startsWith("192.168.150.")
                || ip.startsWith("192.168.56.")
                || ip.startsWith("10.0.2."); // VirtualBox NAT模式常见网段
    }

    /**
     * 根据IP地址计算workerId
     * @param ip IP地址
     * @param workerIdBits workerId位数 机器 ID 的最大*= (1 << workerIdBits) - 1
     *                    3: 0-7
     *                    4: 0-15
     *                    5: 0-31
     *                    6: 0-63
     *                    7: 0-127
     *                    8: 0-255
     *                    9: 0-511
     * @return workerId
     */
    public static int getWorkerIdByIp(String ip, int workerIdBits) {
        // 1. 计算IP的CRC32哈希*
        CRC32 crc32 = new CRC32();
        crc32.update(ip.getBytes());
        long hash = crc32.getValue();
        // 2. 取模映射*workerIdBits 范围* ~ 2^workerIdBits - 1*
        int maxWorkerId = (1 << workerIdBits) - 1;
        return (int) (hash & maxWorkerId); // 等价*hash % (maxWorkerId + 1)
    }
}
