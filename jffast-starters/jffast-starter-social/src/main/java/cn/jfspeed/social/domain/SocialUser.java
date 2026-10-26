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
@Schema(title = "社交用户信息")
public class SocialUser {

    @Schema(title = "社交平台标识")
    private String provider;

    @Schema(title = "社交平台用户ID")
    private String openId;

    @Schema(title = "用户名")
    private String username;

    @Schema(title = "昵称")
    private String nickname;

    @Schema(title = "头像URL")
    private String avatarUrl;

    @Schema(title = "邮箱")
    private String email;

    @Schema(title = "原始用户信息（JSON）")
    private String rawUserInfo;
}
