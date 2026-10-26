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
 * @author jffast
 */
@Data
@Schema(title = "社交登录结果")
public class SocialLoginResult {

    @Schema(title = "社交用户信息（仅用于展示，绑定必须使用 bindTicket）")
    private SocialUser socialUser;

    @Schema(title = "绑定票据：调用 /social/bind 时提交，服务端验签后绑定")
    private String bindTicket;

    @Schema(title = "系统 Token（登录成功时返回")
    private String token;

    @Schema(title = "是否已绑定系统账号")
    private Boolean bound;

    @Schema(title = "绑定的系统用户ID（已绑定时返回")
    private Long userId;

    @Schema(title = "认证成功后前端跳转地址")
    private String targetUri;
}
