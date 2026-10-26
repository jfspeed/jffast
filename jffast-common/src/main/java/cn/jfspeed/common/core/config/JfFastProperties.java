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
 * RSA 密钥配置属性
 * <p>
 * 用于前端密码加密传输，客户端使用公钥加密，服务端使用私钥解密。
 * </p>
 *
 * <pre>
 * jffast:
 *   rsa:
 *     privateKey: MIIBojANBgkqh...
 *     publicKey: MIIBIjANBgkqh...
 * </pre>
 *
 * @author jffast
 */
@Data
@Component
@ConfigurationProperties(prefix = "jffast.rsa")
public class JfFastProperties {

    /** RSA 私钥（服务端解密用，必填） */
    private String privateKey;

    /** RSA 公钥（下发给前端加密用，必填） */
    private String publicKey;
}
