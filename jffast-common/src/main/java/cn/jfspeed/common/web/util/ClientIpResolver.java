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

import java.util.Collection;
import java.util.List;

/**
 * 真实客户端 IP 的统一解析入口。
 * <p>
 * 存在的理由：{@code X-Forwarded-For} 是客户端可控的请求头，直接取首跳等于把「按 IP 限流 /
 * IP 白名单 / 审计定责」做成客户端一句话就能绕过的摆设。解析算法（可信代理回溯）集中在
 * {@link IPUtil#getClientIp}，本类只负责把「可信代理 CIDR 列表」这一份配置喂给它 ——
 * 于是「谁在按 IP 做判定」与「代理拓扑长什么样」解耦：限流、日志、IP 白名单都调同一个 Bean，
 * 运维只需配一次。
 * <p>
 * <b>为什么放在 common 而不是安全 Starter</b>：{@code jffast-starter-log} 对
 * {@code jffast-starter-security} 声明的是 optional 依赖（允许「只打日志不过鉴权」的部署形态）。
 * 若本类留在安全 Starter，日志切面的构造器签名就会引用一个可能不在类路径上的类型，
 * Spring 解析 {@code ObjectProvider} 泛型时直接抛异常，缺安全 Starter 的应用起不来。
 * 下沉到 common 后日志切面只依赖必然存在的类型，安全 Starter 依旧负责从
 * {@code jffast.security.trusted-proxies} 装配 Bean。
 * <p>
 * 未装配 Bean 时的兜底语义（由调用方用空列表构造实例实现）：一律退化为「只认 TCP 对端地址」，
 * 而不是退回可伪造的 {@link IPUtil#getIpAddress()}。
 *
 * @author jffast
 */
public class ClientIpResolver {

    private final Collection<String> trustedProxies;

    /**
     * @param trustedProxies 可信代理 CIDR / 地址字面量列表；null 或空表示「不信任任何转发头」，
     *                       此时等价于直接取 TCP 对端地址
     */
    public ClientIpResolver(Collection<String> trustedProxies) {
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies;
    }

    /**
     * 解析当前请求的真实客户端 IP；不可判定时返回 TCP 对端地址，不会返回 null。
     */
    public String resolve(HttpServletRequest request) {
        return IPUtil.getClientIp(request, trustedProxies);
    }
}
