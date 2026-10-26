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
package cn.jfspeed.common.core.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import cn.jfspeed.common.core.util.SensitiveMaskUtil;

/**
 * Logback 日志消息脱敏转换器。
 *
 * <p>对日志消息中的常见敏感信息（手机号、身份证号、邮箱、密码/token/密钥等）进行正则脱敏。
 * 在 logback-spring.xml 中通过 {@code <conversionRule conversionWord="msg" converterClass="..."/>}
 * 注册后可全局生效。</p>
 *
 * @author jffast
 */
public class SensitiveMaskMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String original = super.convert(event);
        return SensitiveMaskUtil.maskText(original);
    }
}
