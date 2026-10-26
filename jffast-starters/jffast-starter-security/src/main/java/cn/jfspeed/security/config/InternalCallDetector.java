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
package cn.jfspeed.security.config;

/**
 * 「已验签的内部调用」识别 SPI。
 *
 * <h3>为什么需要它</h3>
 * 分布式部署下存在两条调用链，身份机制不同：
 * <ul>
 *   <li><b>客户端 → 服务</b>：走 Sa-Token，请求头带 {@code token}，{@link SaTokenConfig}
 *       的 {@code StpUtil.checkLogin()} 能拿到会话；</li>
 *   <li><b>服务 → 服务</b>：透传的是共享密钥签名的身份头（如 {@code X-User-Id} / {@code X-User-Sign}），
 *       下游验签后写入<strong>自己的</strong>用户上下文，<b>没有</b> {@code token}。</li>
 * </ul>
 * 于是第二种调用一旦打到「需登录」的接口，就会抛 {@code NotLoginException} 并被兜成 500。
 * 本 SPI 让**被调用侧**显式声明"当前请求是不是已验签的内部调用"，从而跳过登录校验。
 *
 * <h3>安全边界（实现方必须保证）</h3>
 * 实现 {@link #isTrustedInternalCall()} 返回 true <b>必须</b>基于「调用方持有共享密钥」的证据
 * （如 {@code X-User-Sign} 验签通过），<b>绝不能</b>基于请求里出现的裸 {@code X-User-Id} 之类的可伪造头部。
 * 只要这条守住，"跳过登录校验"就等价于"已认证"，不会造成越权。
 *
 * <h3>单体形态</h3>
 * 单体（jffast-boot）<b>不提供</b>本接口的实现，{@link SaTokenConfig} 会按
 * "非内部调用"处理，行为与引入本 SPI 前完全一致。
 *
 * @author jffast
 */
public interface InternalCallDetector {

    /**
     * 当前请求是否为「已验签的内部调用」。实现必须只依据不可伪造的证据（共享密钥签名）判断。
     */
    boolean isTrustedInternalCall();
}
