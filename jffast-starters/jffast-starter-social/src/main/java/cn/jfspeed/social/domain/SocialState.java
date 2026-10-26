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
 * OAuth 授权 state：携带平台、前端回跳地址与过期时间，并做 HMAC 签名。
 *
 * @author jffast
 */
@Data
public class SocialState {

    private String random;

    private String provider;

    private String targetUri;

    /** 过期时间（epoch millis），签发时写入，回调时必须校验 */
    private Long expiresAt;

    public String encode(String secret) {
        return SignedStateUtil.sign(JSONUtil.toJsonStr(this), secret);
    }

    public static SocialState decode(String encoded, String secret) {
        SocialState state;
        try {
            state = JSONUtil.toBean(SignedStateUtil.verifyAndGetPayload(encoded, secret), SocialState.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 解析失败");
        }
        if (StrUtil.isBlank(state.getProvider())) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 缺少平台标识");
        }
        if (state.getExpiresAt() == null || state.getExpiresAt() < System.currentTimeMillis()) {
            throw new BusinessException(FrameworkErrorCode.PARAM_ERROR.getCode(), "state 已过期，请重新发起授权");
        }
        return state;
    }
}
