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

/**
 * 通知发送通道 SPI。
 * <p>
 * 一条通知固定落库 + 站内信/WebSocket（见 {@code SysNotificationService}），本接口负责
 * <b>额外通道</b>（邮件、短信等）的单目标投递。实现类注册为 Spring Bean 后由
 * {@link NotificationChannelDispatcher} 按通道类型分发；未启用的通道跳过，不阻塞站内信。
 *
 * @author jffast
 */
public interface NotificationChannel {

    /**
     * 通道类型标识（如 email / sms），与发送请求里的 channels 取值对应。
     */
    String getType();

    /**
     * 通道是否已启用（未配置/未开启时返回 false，分发时跳过并告警）。
     */
    boolean isEnabled();

    /**
     * 向单个目标投递一条通知（目标为邮箱地址或手机号，由通道语义决定）。
     *
     * @throws Exception 投递失败时抛出，由分发器统一记录，不影响站内信与其它收件人
     */
    void send(String target, String title, String content) throws Exception;
}
