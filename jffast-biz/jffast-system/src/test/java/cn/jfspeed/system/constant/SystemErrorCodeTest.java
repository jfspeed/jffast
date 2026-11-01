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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * system 业务错误码段位与客户端可见码值契约。
 *
 * @author jffast
 */
class SystemErrorCodeTest {

    @Test
    @DisplayName("验证码错误码固定为 1007-1009：前端据此展开/刷新验证码")
    void captchaCodesAreStable() {
        assertEquals(1007, SystemErrorCode.CAPTCHA_REQUIRED.getCode());
        assertEquals(1008, SystemErrorCode.CAPTCHA_EXPIRED.getCode());
        assertEquals(1009, SystemErrorCode.CAPTCHA_INVALID.getCode());
    }

    @Test
    @DisplayName("码值唯一且落在 system 段位 1000-1099")
    void codesAreUniqueAndInSystemSegment() {
        Set<Integer> seen = new HashSet<>();
        for (SystemErrorCode code : SystemErrorCode.values()) {
            assertTrue(code.getCode() >= 1000 && code.getCode() <= 1099,
                    code.name() + " 码值越出 system 段位：" + code.getCode());
            assertTrue(seen.add(code.getCode()),
                    "码值重复：" + code.getCode() + "（" + code.name() + "）");
        }
    }
}
