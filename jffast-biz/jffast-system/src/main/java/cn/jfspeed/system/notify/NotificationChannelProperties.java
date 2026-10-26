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
package cn.jfspeed.system.notify;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 通知通道配置（邮件 / 短信）。
 * <p>
 * 配置前缀 {@code jffast.system.notification.channel}；两个通道默认关闭，
 * 服务端侧还需要配套的通道配置（邮件为标准 {@code spring.mail.*}，短信为自建/第三方 HTTP 网关）。
 *
 * @author jffast
 */
@Data
@Component
@ConfigurationProperties(prefix = "jffast.system.notification.channel")
public class NotificationChannelProperties {

    /** 邮件通道 */
    private Email email = new Email();

    /** 短信通道 */
    private Sms sms = new Sms();

    @Data
    public static class Email {

        /** 是否启用邮件通道（还需配置 spring.mail.* 才真正可发） */
        private boolean enabled = false;

        /** 发件人地址；为空时使用 spring.mail.username */
        private String from;
    }

    @Data
    public static class Sms {

        /** 是否启用短信通道 */
        private boolean enabled = false;

        /** 短信网关地址（POST，body 为 body-template 渲染结果） */
        private String url;

        /** 追加请求头（如鉴权头），键值对 */
        private Map<String, String> headers = new LinkedHashMap<>();

        /**
         * 请求体模板，占位符 {@code ${phone}} / {@code ${title}} / {@code ${content}} 会被替换为
         * JSON 转义后的值（模板里的占位符自带引号即可）。默认模板适配大多数 HTTP 短信网关。
         */
        private String bodyTemplate = "{\"phone\":\"${phone}\",\"title\":\"${title}\",\"content\":\"${content}\"}";
    }
}
