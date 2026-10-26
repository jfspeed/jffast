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
}
