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
package cn.jfspeed.common.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 系统级可配置常量（初始密码等）。
 * <p>
 * <b>初始密码</b>：不提供任何默认值，未配置时为 {@code null}。
 * 各使用方（{@code DataInitializer} / {@code SysUserService#resetPassword} /
 * {@code importUsers}）应自行降级：随机生成强口令（见 {@code PasswordGenerator}）
 * 并回传给操作者，而<b>不是</b>落回某个固定密码。
 *
 * <pre>
 * jffast:
 *   system:
 *     init-password: ${JFFAST_INIT_PASSWORD:}
 *     # 与 init-password 的开箱即用默认值保持一致（Pro / Cloud 是 A@123456）
 *     public-test-password: test1234
 * </pre>
 *
 * @author jffast
 */
@Data
@Component
@ConfigurationProperties(prefix = "jffast.system")
public class JfFastSystemProperties {

    /** 新建/重置用户时使用的初始密码；同时也是「新密码不得与初始密码相同」的比对基准。为空表示未配置 */
    private String initPassword;

    /**
     * 各栈 yml 里 {@code init-password} 的「开箱即用示例值」——它不是密钥，是随仓库公开、
     * 任何人都能读到的填充值，用它初始化 admin 时必须打 WARN 提醒换掉。
     * <p>
     * 之所以是配置项而不是代码常量：单体栈的示例值是 {@code test1234}，Pro 与 Cloud 栈是
     * {@code A@123456}（改常量就得为每个栈复制一份初始化逻辑，两份迟早漂移——这正是
     * {@code DataInitializer} 从各可运行应用模块下移到本模块之前的状态）。现在逻辑只有一份，
     * 差异留在各自 yml 的一行配置里。
     */
    private String publicTestPassword = "test1234";
}
