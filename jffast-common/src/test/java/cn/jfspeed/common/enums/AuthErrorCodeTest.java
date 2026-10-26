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
package cn.jfspeed.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;

/**
 * @author jffast
 */
class AuthErrorCodeTest {

    @Test
    @DisplayName("LOGIN_EXPIRED 应为 1003 且 message 非空")
    void loginExpiredShouldBe1003() {
        assertEquals(1003, AuthErrorCode.LOGIN_EXPIRED.getCode());
        assertNotNull(AuthErrorCode.LOGIN_EXPIRED.getMsg());
        assertFalse(AuthErrorCode.LOGIN_EXPIRED.getMsg().isEmpty());
    }

    @Test
    @DisplayName("FORBIDDEN 应为 403 且 message 非空")
    void forbiddenShouldBe403() {
        assertEquals(403, AuthErrorCode.FORBIDDEN.getCode());
        assertNotNull(AuthErrorCode.FORBIDDEN.getMsg());
        assertFalse(AuthErrorCode.FORBIDDEN.getMsg().isEmpty());
    }

    @Test
    @DisplayName("BusinessException(IErrorCode) 应带上 code/msg")
    void businessExceptionShouldCarryCodeAndMsg() {
        BusinessException ex = new BusinessException(AuthErrorCode.LOGIN_EXPIRED);
        assertEquals(1003, ex.getCode());
        assertEquals(AuthErrorCode.LOGIN_EXPIRED.getMsg(), ex.getMessage());
    }
}
