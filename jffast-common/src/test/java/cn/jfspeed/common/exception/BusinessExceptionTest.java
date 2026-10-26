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
package cn.jfspeed.common.exception;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.core.enums.IErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class BusinessExceptionTest {

    /** 创建一个简单的 IErrorCode 实例用于测试 */
    private IErrorCode testErrorCode(int code, String msg) {
        return new IErrorCode() {
            @Override public int getCode() { return code; }
            @Override public String getMsg() { return msg; }
        };
    }

    @Nested
    @DisplayName("构造函数")
    class Constructor {

        @Test
        @DisplayName("message 构造应设置消息和默认 code 400")
        void shouldSetMessage() {
            BusinessException ex = new BusinessException("操作失败");
            assertEquals("操作失败", ex.getMessage());
            assertEquals(400, ex.getCode());
        }

        @Test
        @DisplayName("code + message 构造应设置 code 和消息")
        void shouldSetCodeAndMessage() {
            BusinessException ex = new BusinessException(500, "系统错误");
            assertEquals(500, ex.getCode());
            assertEquals("系统错误", ex.getMessage());
        }

        @Test
        @DisplayName("code + message + method 构造应设置全部字段")
        void shouldSetCodeMessageAndMethod() {
            BusinessException ex = new BusinessException(500, "系统错误", "getData");
            assertEquals(500, ex.getCode());
            assertEquals("系统错误", ex.getMessage());
            assertEquals("getData", ex.getMethod());
        }

        @Test
        @DisplayName("message + cause 构造应设置消息和原因")
        void shouldSetMessageAndCause() {
            RuntimeException cause = new RuntimeException("底层异常");
            BusinessException ex = new BusinessException("业务异常", cause);
            assertEquals("业务异常", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("IErrorCode 构造应提取 code 和 msg")
        void shouldUseErrorCode() {
            IErrorCode errorCode = testErrorCode(1001, "用户不存在");
            BusinessException ex = new BusinessException(errorCode);
            assertEquals(1001, ex.getCode());
            assertEquals("用户不存在", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("getter/setter")
    class Accessors {

        @Test
        @DisplayName("setMethod 应更新 method 字段")
        void shouldSetMethod() {
            BusinessException ex = new BusinessException("错误");
            assertNull(ex.getMethod());
            ex.setMethod("testMethod");
            assertEquals("testMethod", ex.getMethod());
        }

        @Test
        @DisplayName("setCode 应覆盖构造时设置的 code")
        void shouldOverrideCode() {
            BusinessException ex = new BusinessException("错误");
            assertEquals(400, ex.getCode());
            ex.setCode(500);
            assertEquals(500, ex.getCode());
        }

        @Test
        @DisplayName("setMessage 应覆盖构造时设置的 message")
        void shouldOverrideMessage() {
            BusinessException ex = new BusinessException("原始消息");
            ex.setMessage("新消息");
            assertEquals("新消息", ex.getMessage());
        }
    }
}
