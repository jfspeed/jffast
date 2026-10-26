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
package cn.jfspeed.common.security.spi;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 登录安全策略扩展点。
 * <p>
 * Base 版只提供最基础的能力（固定阈值的失败计数、固定强度的密码校验）；
 * 扩展实现本接口以提供「可配置的密码策略 + IP 白名单 + 登录限制」，
 * 无需改动 Base 登录代码。
 * <p>
 * <b>调用约定</b>：Base 侧通过 {@code ObjectProvider#getIfAvailable()} 可选获取实现，
 * 未提供实现（Base 单独运行）时全部回调为空实现，行为与改造前一致。
 * <p>
 * <b>异常约定</b>：需要拦截登录/改密时抛
 * {@link cn.jfspeed.common.core.exception.BusinessException}，
 * Base 的登录流程会把它转成 {@code R.error(code, message)} 返回给前端。
 *
 * @author jffast
 */
public interface LoginPolicyProvider {

    /**
     * 登录前置校验（在读取密码、验密之前调用）。
     * <p>
     * 典型用途：IP 白名单、单 IP/单账号的登录频率限制。
     *
     * @param userName 登录账号（手机号登录时传手机号），可能为空
     * @param request  当前请求，用于取客户端 IP / User-Agent
     */
    default void checkLoginAllowed(String userName, HttpServletRequest request) {
    }

    /**
     * 密码校验通过后回调。
     * <p>
     * 典型用途：清零该账号的失败计数、记录成功登录的 IP、更新「上次登录」信息。
     *
     * @param userId   用户 ID
     * @param userName 登录账号
     * @param request  当前请求
     */
    default void onLoginSuccess(Long userId, String userName, HttpServletRequest request) {
    }

    /**
     * 密码校验失败后回调。
     * <p>
     * 典型用途：按可配置阈值累加失败次数并触发临时锁定（超出 Base 固定阈值策略的部分）。
     *
     * @param userName 登录账号
     * @param request  当前请求
     */
    default void onLoginFailure(String userName, HttpServletRequest request) {
    }

    /**
     * 修改密码时的密码策略校验（在写库之前调用）。
     * <p>
     * 典型用途：复杂度规则可配置、禁止使用最近 N 次历史密码、弱口令字典拦截。
     *
     * @param userId      用户 ID（用于查历史密码）
     * @param rawPassword 明文新密码
     */
    default void validateNewPassword(Long userId, String rawPassword) {
    }
}
