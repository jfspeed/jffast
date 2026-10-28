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
package cn.jfspeed.social.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 第三方账号授权回调的结果。
 *
 * <p>本 Starter 只做**社交账号绑定**：回调拿到第三方用户信息，签发绑定票据；绑定动作本身在
 * {@code /social/bind}（需登录态）里完成，之后第三方账号与该系统用户建立关联。
 *
 * <p>因此这里**不返回系统登录 Token** —— 「凭社交账号直接登录」需要业务方在绑定关系之上
 * 自行签发会话（本 Starter 不落这一层，避免把第三方身份直接当成系统登录凭证）。
 *
 * @author jffast
 */
@Data
@Schema(title = "社交账号授权结果")
public class SocialLoginResult {

    @Schema(title = "社交用户信息（仅用于展示，绑定必须使用 bindTicket）")
    private SocialUser socialUser;

    @Schema(title = "绑定票据：调用 /social/bind 时提交，服务端验签后绑定")
    private String bindTicket;

    @Schema(title = "是否已绑定系统账号")
    private Boolean bound;

    @Schema(title = "绑定的系统用户ID（不再回吐：回调只返回 bound，归属判定在 /social/bind）")
    private Long userId;

    @Schema(title = "认证成功后前端跳转地址")
    private String targetUri;
}
