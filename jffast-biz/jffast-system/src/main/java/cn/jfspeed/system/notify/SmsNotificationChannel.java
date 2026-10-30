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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.system.constant.NotificationConstant;

import lombok.extern.slf4j.Slf4j;

/**
 * 短信通知通道（通用 HTTP 短信网关）。
 * <p>
 * 各家短信服务（阿里云/腾讯云/自建网关）差异大且需私钥/签名配置，这里不绑定具体 SDK，
 * 而是按 {@code POST <url> + JSON 请求体} 对接任意 HTTP 网关：请求体由
 * {@code jffast.system.notification.channel.sms.body-template} 渲染
 * （占位符 {@code ${phone}} / {@code ${title}} / {@code ${content}}，值做 JSON 转义），
 * 鉴权等放在 {@code headers} 配置里。非 2xx 响应视为投递失败。
 *
 * @author jffast
 */
@Slf4j
@Component
public class SmsNotificationChannel implements NotificationChannel {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final NotificationChannelProperties properties;

    public SmsNotificationChannel(NotificationChannelProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getType() {
        return NotificationConstant.CHANNEL_SMS;
    }

    @Override
    public boolean isEnabled() {
        return properties.getSms().isEnabled() && StrUtil.isNotBlank(properties.getSms().getUrl());
    }

    @Override
    public void send(String target, String title, String content) throws Exception {
        NotificationChannelProperties.Sms sms = properties.getSms();
        if (StrUtil.isBlank(sms.getUrl())) {
            throw new IllegalStateException("未配置短信网关地址（jffast.system.notification.channel.sms.url）");
        }
        String body = renderBody(sms.getBodyTemplate(), target, title, content);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(sms.getUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> header : sms.getHeaders().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }

        HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            // 异常消息上游会以 log.error 整体打出（NotificationChannelDispatcher），把网关响应体
            // 拼进去等于把下游回执（可能含号码明细、流水、余额）写进本系统日志。因此消息体只放
            // 状态码，详情走 debug 并截断。
            log.debug("短信网关响应体（截断 500 字符）：status={}, body={}",
                    response.statusCode(), StrUtil.sub(response.body(), 0, 500));
            throw new IllegalStateException("短信网关返回 HTTP " + response.statusCode());
        }
    }

    /**
     * 渲染请求体模板：占位符替换为 JSON 转义后的值（模板中占位符自带引号）。
     */
    static String renderBody(String template, String phone, String title, String content) {
        String tpl = StrUtil.blankToDefault(template, "{\"phone\":\"${phone}\",\"title\":\"${title}\",\"content\":\"${content}\"}");
        return tpl.replace("${phone}", jsonEscape(phone))
                .replace("${title}", jsonEscape(title))
                .replace("${content}", jsonEscape(content));
    }

    private static String jsonEscape(String value) {
        String quoted = JSONUtil.toJsonStr(value == null ? "" : value);
        // 去掉外层引号，保留转义后的内容
        return quoted.substring(1, quoted.length() - 1);
    }
}
