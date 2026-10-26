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
package cn.jfspeed.common.util;

import cn.jfspeed.common.core.util.CheckUtils;
import cn.jfspeed.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class CheckUtilsTest {

    @Nested
    @DisplayName("checkNull")
    class CheckNull {

        @Test
        @DisplayName("null 对象应抛异常")
        void shouldThrowOnNull() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkNull(null));
        }

        @Test
        @DisplayName("非 null 对象不应抛异常")
        void shouldNotThrowOnNonNull() {
            assertDoesNotThrow(() -> CheckUtils.checkNull("hello"));
        }

        @Test
        @DisplayName("自定义消息应生效")
        void shouldUseCustomMessage() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> CheckUtils.checkNull(null, "自定义消息"));
            assertEquals("自定义消息", ex.getMessage());
        }
    }

    @Nested
    @DisplayName("checkEmpty")
    class CheckEmpty {

        @Test
        @DisplayName("空字符串应抛异常")
        void shouldThrowOnEmptyString() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty(""));
        }

        @Test
        @DisplayName("空白字符串应抛异常")
        void shouldThrowOnBlankString() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty("   "));
        }

        @Test
        @DisplayName("null 字符串应抛异常")
        void shouldThrowOnNullString() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty((String) null));
        }

        @Test
        @DisplayName("空集合应抛异常")
        void shouldThrowOnEmptyCollection() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty(Collections.emptyList()));
        }

        @Test
        @DisplayName("空 Map 应抛异常")
        void shouldThrowOnEmptyMap() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty(Collections.emptyMap()));
        }

        @Test
        @DisplayName("空数组应抛异常")
        void shouldThrowOnEmptyArray() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmpty(new Object[]{}));
        }
    }

    @Nested
    @DisplayName("checkRange")
    class CheckRange {

        @Test
        @DisplayName("范围内数值不应抛异常")
        void shouldNotThrowInRange() {
            assertDoesNotThrow(() -> CheckUtils.checkRange(5, 1, 10));
        }

        @Test
        @DisplayName("边界值不应抛异常")
        void shouldNotThrowOnBoundary() {
            assertDoesNotThrow(() -> CheckUtils.checkRange(1, 1, 10));
            assertDoesNotThrow(() -> CheckUtils.checkRange(10, 1, 10));
        }

        @Test
        @DisplayName("超出范围应抛异常")
        void shouldThrowOutOfRange() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkRange(0, 1, 10));
            assertThrows(BusinessException.class, () -> CheckUtils.checkRange(11, 1, 10));
        }
    }

    @Nested
    @DisplayName("checkLength")
    class CheckLength {

        @Test
        @DisplayName("长度在范围内不应抛异常")
        void shouldNotThrowInLengthRange() {
            assertDoesNotThrow(() -> CheckUtils.checkLength("abc", 1, 10));
        }

        @Test
        @DisplayName("长度超出范围应抛异常")
        void shouldThrowOutOfLengthRange() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkLength("", 1, 10));
            assertThrows(BusinessException.class, () -> CheckUtils.checkLength("a".repeat(11), 1, 10));
        }
    }

    @Nested
    @DisplayName("checkEmail")
    class CheckEmail {

        @Test
        @DisplayName("合法邮箱不应抛异常")
        void shouldNotThrowOnValidEmail() {
            assertDoesNotThrow(() -> CheckUtils.checkEmail("test@example.com"));
        }

        @Test
        @DisplayName("非法邮箱应抛异常")
        void shouldThrowOnInvalidEmail() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkEmail("not-an-email"));
        }
    }

    @Nested
    @DisplayName("checkPhone")
    class CheckPhone {

        @Test
        @DisplayName("合法手机号不应抛异常")
        void shouldNotThrowOnValidPhone() {
            assertDoesNotThrow(() -> CheckUtils.checkPhone("13800138000"));
        }

        @Test
        @DisplayName("非法手机号应抛异常")
        void shouldThrowOnInvalidPhone() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkPhone("12345678901"));
        }
    }

    @Nested
    @DisplayName("checkIsTrue / checkIsFalse")
    class CheckBoolean {

        @Test
        @DisplayName("checkIsTrue true 不抛异常")
        void shouldNotThrowOnTrue() {
            assertDoesNotThrow(() -> CheckUtils.checkIsTrue(true));
        }

        @Test
        @DisplayName("checkIsTrue false 抛异常")
        void shouldThrowOnFalse() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkIsTrue(false));
        }

        @Test
        @DisplayName("checkIsFalse false 不抛异常")
        void shouldNotThrowOnFalse() {
            assertDoesNotThrow(() -> CheckUtils.checkIsFalse(false));
        }

        @Test
        @DisplayName("checkIsFalse true 抛异常")
        void shouldThrowOnTrue() {
            assertThrows(BusinessException.class, () -> CheckUtils.checkIsFalse(true));
        }
    }

    @Nested
    @DisplayName("checkNotIn / checkIn")
    class CheckInList {

        @Test
        @DisplayName("checkNotIn 元素在列表中不应抛异常")
        void shouldNotThrowWhenInList() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertDoesNotThrow(() -> CheckUtils.checkNotIn("a", list));
        }

        @Test
        @DisplayName("checkNotIn 元素不在列表中应抛异常")
        void shouldThrowWhenNotInList() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertThrows(BusinessException.class, () -> CheckUtils.checkNotIn("d", list));
        }

        @Test
        @DisplayName("checkIn 元素在列表中应抛异常")
        void shouldThrowWhenInListForCheckIn() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertThrows(BusinessException.class, () -> CheckUtils.checkIn("a", list));
        }
    }
}
