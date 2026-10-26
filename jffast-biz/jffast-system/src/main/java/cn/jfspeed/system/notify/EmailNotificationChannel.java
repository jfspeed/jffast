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

import java.nio.charset.StandardCharsets;

import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.system.constant.NotificationConstant;

import lombok.extern.slf4j.Slf4j;

/**
 * 邮件通知通道（SMTP）。
 * <p>
 * 依赖标准 {@code spring.mail.*} 配置（host/port/username/password）与
 * {@code jffast.system.notification.channel.email.enabled=true}；两者齐备才启用。
 * 按收件人逐封发送，避免泄露收件人地址；内容含 HTML 标签时按 HTML 邮件发送。
 *
 * @author jffast
 */
@Slf4j
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationChannelProperties properties;

    /** 发件人兜底：配置的 from 为空时用 SMTP 账号 */
    @Value("${spring.mail.username:}")
    private String mailUsername;

    public EmailNotificationChannel(ObjectProvider<JavaMailSender> mailSenderProvider,
                                    NotificationChannelProperties properties) {
        this.mailSenderProvider = mailSenderProvider;
        this.properties = properties;
    }

    @Override
    public String getType() {
        return NotificationConstant.CHANNEL_EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return properties.getEmail().isEnabled() && mailSenderProvider.getIfAvailable() != null;
    }

    @Override
    public void send(String target, String title, String content) throws Exception {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("未配置邮件服务（spring.mail.*），无法发送邮件通知");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
        String from = StrUtil.blankToDefault(properties.getEmail().getFrom(), mailUsername);
        if (StrUtil.isNotBlank(from)) {
            helper.setFrom(from);
        }
        helper.setTo(target);
        helper.setSubject(StrUtil.blankToDefault(title, "通知"));
        boolean html = content != null && content.contains("<");
        helper.setText(content == null ? "" : content, html);
        sender.send(message);
    }
}
