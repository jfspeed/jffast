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
package cn.jfspeed.common.domain;

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import cn.jfspeed.common.web.domain.dto.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class RTest {

    @Nested
    @DisplayName("R.success()")
    class Success {

        @Test
        @DisplayName("success() 应返回 code=200, message=ok")
        void shouldReturnSuccessWithoutData() {
            R<Void> r = R.success();
            assertEquals(200, r.getCode());
            assertEquals("ok", r.getMessage());
            assertNull(r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("success(data) 应返回 code=200 并携带数据")
        void shouldReturnSuccessWithData() {
            R<String> r = R.success("hello");
            assertEquals(200, r.getCode());
            assertEquals("hello", r.getData());
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("successMsg(msg) 应返回自定义消息")
        void shouldReturnSuccessWithCustomMsg() {
            R<Void> r = R.successMsg("操作成功");
            assertEquals(200, r.getCode());
            assertEquals("操作成功", r.getMessage());
        }
    }

    @Nested
    @DisplayName("R.error()")
    class Error {

        @Test
        @DisplayName("error(msg) 默认 400：业务失败不再冒充服务端故障")
        void shouldReturnErrorWithMessage() {
            R<Void> r = R.error("出错了");
            assertEquals(FrameworkErrorCode.PARAM_ERROR.getCode(), r.getCode());
            assertEquals("出错了", r.getMessage());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("error(code, msg) 应返回指定 code 和消息")
        void shouldReturnErrorWithCodeAndMessage() {
            R<Void> r = R.error(403, "禁止访问");
            assertEquals(403, r.getCode());
            assertEquals("禁止访问", r.getMessage());
            assertFalse(r.isSuccess());
        }

        @Test
        @DisplayName("error(msg, data) 同样默认 400")
        void shouldReturnErrorWithData() {
            R<String> r = R.error("验证失败", "field: name");
            assertEquals(FrameworkErrorCode.PARAM_ERROR.getCode(), r.getCode());
            assertEquals("验证失败", r.getMessage());
            assertEquals("field: name", r.getData());
        }
    }

    @Nested
    @DisplayName("R.status()")
    class Status {

        @Test
        @DisplayName("status(true) 应返回成功")
        void shouldReturnSuccessOnTrue() {
            R<Void> r = R.status(true);
            assertTrue(r.isSuccess());
        }

        @Test
        @DisplayName("status(false) 应返回失败")
        void shouldReturnErrorOnFalse() {
            R<Void> r = R.status(false);
            assertFalse(r.isSuccess());
        }
    }
}
