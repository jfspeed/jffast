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

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class FrameworkErrorCodeTest {

    @Nested
    @DisplayName("基本属性")
    class Properties {

        @Test
        @DisplayName("SUCCESS 应返回 code=200")
        void shouldHaveSuccessCode() {
            assertEquals(200, FrameworkErrorCode.SUCCESS.getCode());
        }

        @Test
        @DisplayName("SYSTEM_ERROR 应返回非 200 code")
        void shouldHaveSystemErrorCode() {
            assertNotEquals(200, FrameworkErrorCode.SYSTEM_ERROR.getCode());
        }

        @Test
        @DisplayName("每个枚举值都应有 msg")
        void shouldHaveMessage() {
            for (FrameworkErrorCode code : FrameworkErrorCode.values()) {
                assertNotNull(code.getMsg());
                assertFalse(code.getMsg().isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("getByHttpCode")
    class GetByHttpCode {

        @Test
        @DisplayName("应根据 HTTP 状态码找到对应错误码")
        void shouldFindByHttpCode() {
            assertEquals(FrameworkErrorCode.HTTP_400_BAD_REQUEST, FrameworkErrorCode.getByHttpCode(400));
            assertEquals(FrameworkErrorCode.HTTP_401_UNAUTHORIZED, FrameworkErrorCode.getByHttpCode(401));
            assertEquals(FrameworkErrorCode.HTTP_403_FORBIDDEN, FrameworkErrorCode.getByHttpCode(403));
            assertEquals(FrameworkErrorCode.HTTP_404_NOT_FOUND, FrameworkErrorCode.getByHttpCode(404));
            assertEquals(FrameworkErrorCode.HTTP_500_INTERNAL_ERROR, FrameworkErrorCode.getByHttpCode(500));
            assertEquals(FrameworkErrorCode.HTTP_502_BAD_GATEWAY, FrameworkErrorCode.getByHttpCode(502));
            assertEquals(FrameworkErrorCode.HTTP_503_SERVICE_UNAVAILABLE, FrameworkErrorCode.getByHttpCode(503));
            assertEquals(FrameworkErrorCode.HTTP_504_GATEWAY_TIMEOUT, FrameworkErrorCode.getByHttpCode(504));
        }

        @Test
        @DisplayName("未匹配的 HTTP 状态码应返回 SYSTEM_ERROR (default)")
        void shouldReturnSystemErrorForUnknownHttpCode() {
            FrameworkErrorCode code = FrameworkErrorCode.getByHttpCode(999);
            assertEquals(FrameworkErrorCode.SYSTEM_ERROR, code);
        }
    }
}
