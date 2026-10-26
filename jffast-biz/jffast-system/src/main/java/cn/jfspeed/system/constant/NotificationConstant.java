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
package cn.jfspeed.system.constant;

/**
 * 通知中心常量。
 * <p>
 * 统一收敛 {@code sys_notification.type}/{@code level}/{@code recipient_type} 的取值，
 * 避免各处散落字符串字面量。
 *
 * @author jffast
 */
public final class NotificationConstant {

    private NotificationConstant() {
    }

    // ─── 通知类型 type ──────────────────────────────

    /** 系统通知（公告、运维等） */
    public static final String TYPE_SYSTEM = "system";

    /** 业务通知（业务模块主动推送） */
    public static final String TYPE_BIZ = "biz";

    /** 流程通知（工作流待办/办结，由流程事件监听器写入） */
    public static final String TYPE_FLOW = "flow";

    // ─── 通知级别 level ─────────────────────────────

    /** 信息 */
    public static final String LEVEL_INFO = "info";

    /** 警告 */
    public static final String LEVEL_WARNING = "warning";

    /** 错误 */
    public static final String LEVEL_ERROR = "error";

    // ─── 接收人类型 recipientType ────────────────────

    /** 全体用户 */
    public static final String RECIPIENT_ALL = "all";

    /** 按角色 */
    public static final String RECIPIENT_ROLE = "role";

    /** 按部门 */
    public static final String RECIPIENT_DEPT = "dept";

    /** 按用户（定向推送） */
    public static final String RECIPIENT_USER = "user";

    // ─── 发送通道 channels ──────────────────────────

    /** 站内信（固定发送，落库 + WebSocket，不需显式指定） */
    public static final String CHANNEL_IN_APP = "in_app";

    /** 邮件（需启用 jffast.system.notification.channel.email 并配置 spring.mail.*） */
    public static final String CHANNEL_EMAIL = "email";

    /** 短信（需启用 jffast.system.notification.channel.sms 并配置网关地址） */
    public static final String CHANNEL_SMS = "sms";

    // ─── 其它 ──────────────────────────────────────

    /** 状态：未发送 */
    public static final Integer STATUS_UNSENT = 0;

    /** 状态：已发送 */
    public static final Integer STATUS_SENT = 1;

    /** 未读 */
    public static final Integer UNREAD = 0;

    /** 已读 */
    public static final Integer READ = 1;

    /** 系统/流程自动发送时的默认发送人 */
    public static final String DEFAULT_SENDER = "系统";
}
