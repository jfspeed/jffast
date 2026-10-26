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
package cn.jfspeed.common.core.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 国际化工具类
 *
 * @author jffast
 */
@Component
public class MessageUtil {

    private static MessageSource messageSource;

    public MessageUtil(MessageSource messageSource) {
        MessageUtil.messageSource = messageSource;
    }

    /**
     * 获取国际化消息
     *
     * @param code 消息代码
     * @return 国际化消息
     */
    public static String getMessage(String code) {
        return getMessage(code, null);
    }

    /**
     * 获取国际化消息
     *
     * @param code 消息代码
     * @param args 参数
     * @return 国际化消息
     */
    public static String getMessage(String code, Object[] args) {
        return getMessage(code, args, null);
    }

    /**
     * 获取国际化消息
     *
     * @param code 消息代码
     * @param args 参数
     * @param defaultMessage 默认消息
     * @return 国际化消息
     */
    public static String getMessage(String code, Object[] args, String defaultMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, defaultMessage, locale);
    }

    /**
     * 获取当前语言
     *
     * @return 当前语言
     */
    public static Locale getLocale() {
        return LocaleContextHolder.getLocale();
    }
}