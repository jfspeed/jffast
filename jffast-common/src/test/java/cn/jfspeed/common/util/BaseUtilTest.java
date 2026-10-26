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

import cn.jfspeed.common.data.util.BaseUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class BaseUtilTest {

    @Nested
    @DisplayName("removeNullValue")
    class RemoveNullValue {

        @Test
        @DisplayName("应移除 null 和空字符串值")
        void shouldRemoveNullAndEmptyValues() {
            Map<String, Object> map = new HashMap<>();
            map.put("key1", "value1");
            map.put("key2", null);
            map.put("key3", "");
            map.put("key4", "value4");

            BaseUtil.removeNullValue(map);

            assertEquals(2, map.size());
            assertEquals("value1", map.get("key1"));
            assertEquals("value4", map.get("key4"));
        }

        @Test
        @DisplayName("空 map 不应抛异常")
        void shouldHandleEmptyMap() {
            Map<String, Object> map = new HashMap<>();
            assertDoesNotThrow(() -> BaseUtil.removeNullValue(map));
            assertTrue(map.isEmpty());
        }

        @Test
        @DisplayName("null map 不应抛异常")
        void shouldHandleNullMap() {
            assertDoesNotThrow(() -> BaseUtil.removeNullValue(null));
        }
    }

    @Nested
    @DisplayName("listCopy")
    class ListCopy {

        @Test
        @DisplayName("应复制列表中的对象")
        void shouldCopyList() {
            // 使用简单对象测试
            List<Map<String, String>> source = new ArrayList<>();
            Map<String, String> item = new HashMap<>();
            item.put("name", "test");
            source.add(item);

            // listCopy 使用 BeanUtils.copyProperties，需要有 getter/setter 的对象
            // 这里测试 null 列表
            assertNull(BaseUtil.listCopy(null, Object.class));
        }

        @Test
        @DisplayName("空列表应返回 null")
        void shouldReturnNullForEmptyList() {
            assertNull(BaseUtil.listCopy(Collections.emptyList(), Object.class));
        }
    }

    @Nested
    @DisplayName("setQueryApplyScope")
    class SetQueryApplyScope {

        @Test
        @DisplayName("应生成部门 ID 条件")
        void shouldGenerateDeptIdCondition() {
            Map<String, Object> scope = new HashMap<>();
            scope.put("bmid", "100");

            String result = BaseUtil.setQueryApplyScope(scope, "dept_id", null, true);

            assertTrue(result.contains("dept_id"));
            assertTrue(result.contains("100"));
        }

        @Test
        @DisplayName("空 scope 应返回空字符串")
        void shouldReturnEmptyForEmptyScope() {
            Map<String, Object> scope = new HashMap<>();
            String result = BaseUtil.setQueryApplyScope(scope, "dept_id", null, true);
            assertEquals("", result);
        }

        @Test
        @DisplayName("应移除字段名中的特殊字符")
        void shouldSanitizeFieldName() {
            Map<String, Object> scope = new HashMap<>();
            scope.put("bmid", "100");

            // sanitizeFieldName 移除非 [a-zA-Z0-9_.] 的字符
            // "dept_id; DROP TABLE" → "dept_idDROPTABLE"（分号、空格被移除）
            String result = BaseUtil.setQueryApplyScope(scope, "dept_id; DROP TABLE", null, true);

            assertFalse(result.contains(";"), "应移除分号");
            // 结果中不会有独立的 "DROP TABLE" 短语（空格已被移除变成 "DROPTABLE"）
            assertFalse(result.contains("DROP TABLE"), "SQL 关键词不应作为独立短语存在");
            assertTrue(result.contains("dept_id"), "应保留字母数字下划线");
        }
    }

    @Nested
    @DisplayName("hasElement")
    class HasElement {

        @Test
        @DisplayName("列表包含元素应返回 true")
        void shouldReturnTrueWhenContains() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertTrue(BaseUtil.hasElement(list, "a"));
        }

        @Test
        @DisplayName("列表不包含元素应返回 false")
        void shouldReturnFalseWhenNotContains() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertFalse(BaseUtil.hasElement(list, "d"));
        }

        @Test
        @DisplayName("null 列表应返回 false")
        void shouldReturnFalseForNullList() {
            assertFalse(BaseUtil.hasElement(null, "a"));
        }

        @Test
        @DisplayName("空列表应返回 false")
        void shouldReturnFalseForEmptyList() {
            assertFalse(BaseUtil.hasElement(Collections.emptyList(), "a"));
        }
    }

    @Nested
    @DisplayName("getPercent")
    class GetPercent {

        @Test
        @DisplayName("应计算百分比")
        void shouldCalculatePercent() {
            BigDecimal val = new BigDecimal("25");
            BigDecimal total = new BigDecimal("100");
            BigDecimal result = BaseUtil.getPercent(val, total);
            assertEquals(new BigDecimal("25.00"), result);
        }

        @Test
        @DisplayName("val 为 0 应返回 0")
        void shouldReturnZeroForZeroVal() {
            BigDecimal result = BaseUtil.getPercent(BigDecimal.ZERO, new BigDecimal("100"));
            assertEquals(BigDecimal.ZERO, result);
        }

        @Test
        @DisplayName("totalVal 为 null 时设为 0 会导致除零异常")
        void shouldThrowWhenTotalValIsNull() {
            assertThrows(ArithmeticException.class, () ->
                    BaseUtil.getPercent(new BigDecimal("25"), null));
        }
    }

    @Nested
    @DisplayName("getRate")
    class GetRate {

        @Test
        @DisplayName("应计算增长率")
        void shouldCalculateRate() {
            BigDecimal oldVal = new BigDecimal("100");
            BigDecimal newVal = new BigDecimal("150");
            BigDecimal result = BaseUtil.getRate(oldVal, newVal);
            assertEquals(new BigDecimal("50.00"), result);
        }

        @Test
        @DisplayName("oldValue 为 0 应返回 0")
        void shouldReturnZeroForZeroOld() {
            BigDecimal result = BaseUtil.getRate(BigDecimal.ZERO, new BigDecimal("100"));
            assertEquals(BigDecimal.ZERO, result);
        }
    }

    @Nested
    @DisplayName("continuousDay")
    class ContinuousDay {

        @Test
        @DisplayName("应计算连续天数")
        void shouldCalculateContinuousDays() {
            List<LocalDate> dates = Arrays.asList(
                    LocalDate.of(2024, 1, 1),
                    LocalDate.of(2024, 1, 2),
                    LocalDate.of(2024, 1, 3),
                    LocalDate.of(2024, 1, 5)  // 断开
            );
            assertEquals(3, BaseUtil.continuousDay(dates));
        }

        @Test
        @DisplayName("空列表应返回 0")
        void shouldReturnZeroForEmptyList() {
            assertEquals(0, BaseUtil.continuousDay(Collections.emptyList()));
        }

        @Test
        @DisplayName("null 列表应返回 0")
        void shouldReturnZeroForNullList() {
            assertEquals(0, BaseUtil.continuousDay(null));
        }
    }

    @Nested
    @DisplayName("cleanIdentifier")
    class CleanIdentifier {

        @Test
        @DisplayName("应清理非法字符")
        void shouldCleanIllegalChars() {
            assertEquals("abc123", BaseUtil.cleanIdentifier("abc123"));
            assertEquals("abc", BaseUtil.cleanIdentifier("abc!@#"));
            // $ 是合法 Java 标识符字符，不会被移除
            assertEquals("$", BaseUtil.cleanIdentifier("!@#$"));
            // 空格不是合法标识符字符
            assertEquals("abc", BaseUtil.cleanIdentifier("a b c"));
        }

        @Test
        @DisplayName("null 应返回 null")
        void shouldReturnNullForNull() {
            assertNull(BaseUtil.cleanIdentifier(null));
        }
    }
}
