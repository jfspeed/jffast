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

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.social.util.SignedStateUtil;
import lombok.Data;

/**
 * 绑定票据：回调换取社交用户信息后由服务端签发，前端凭票据绑定，避免客户端伪造 {@link SocialUser}。
 *
 * @author jffast
 */
@Data
public class SocialBindTicket {

    private String provider;

    private String openId;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String email;

    /** 回调发起时的登录用户（匿名回调为 null），绑定时必须与当前登录用户一致 */
    private Long userId;

    /** 过期时间（epoch millis） */
    private Long expiresAt;

    public String encode(String secret) {
        return SignedStateUtil.sign(JSONUtil.toJsonStr(this), secret);
    }

    public static SocialBindTicket decode(String encoded, String secret) {
        SocialBindTicket ticket;
        try {
            ticket = JSONUtil.toBean(SignedStateUtil.verifyAndGetPayload(encoded, secret), SocialBindTicket.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "绑定票据解析失败");
        }
        if (StrUtil.isBlank(ticket.getProvider()) || StrUtil.isBlank(ticket.getOpenId())) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "绑定票据内容不完整");
        }
        if (ticket.getExpiresAt() == null || ticket.getExpiresAt() < System.currentTimeMillis()) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "绑定票据已过期，请重新授权");
        }
        return ticket;
    }

    public SocialUser toSocialUser() {
        SocialUser user = new SocialUser();
        user.setProvider(provider);
        user.setOpenId(openId);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setEmail(email);
        return user;
    }
}
