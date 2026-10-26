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

import cn.jfspeed.common.core.util.DateUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class DateUtilsTest {

    @Nested
    @DisplayName("format 格式化")
    class Format {

        @Test
        @DisplayName("format(LocalDateTime) 应返回 yyyy-MM-dd HH:mm:ss")
        void shouldFormatLocalDateTime() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals("2024-06-15 14:30:45", DateUtils.format(dt));
        }

        @Test
        @DisplayName("format(null) 应返回 null")
        void shouldReturnNullForNullDateTime() {
            assertNull(DateUtils.format((LocalDateTime) null));
        }

        @Test
        @DisplayName("formatDate(LocalDate) 应返回 yyyy-MM-dd")
        void shouldFormatLocalDate() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            assertEquals("2024-06-15", DateUtils.formatDate(date));
        }

        @Test
        @DisplayName("formatDate(null) 应返回 null")
        void shouldReturnNullForNullDate() {
            assertNull(DateUtils.formatDate(null));
        }

        @Test
        @DisplayName("formatTime 应返回 HH:mm:ss")
        void shouldFormatLocalTime() {
            LocalTime time = LocalTime.of(14, 30, 45);
            assertEquals("14:30:45", DateUtils.formatTime(time));
        }

        @Test
        @DisplayName("formatCompact 应返回紧凑格式")
        void shouldFormatCompact() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals("20240615143045", DateUtils.formatCompact(dt));
        }

        @Test
        @DisplayName("formatCompactDate 应返回紧凑日期格式")
        void shouldFormatCompactDate() {
            LocalDate date = LocalDate.of(2024, 6, 15);
            assertEquals("20240615", DateUtils.formatCompactDate(date));
        }

        @Test
        @DisplayName("format(LocalDateTime, pattern) 应使用自定义格式")
        void shouldFormatWithCustomPattern() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals("2024/06/15", DateUtils.format(dt, "yyyy/MM/dd"));
        }
    }

    @Nested
    @DisplayName("parse 解析")
    class Parse {

        @Test
        @DisplayName("parse 应解析 yyyy-MM-dd HH:mm:ss 格式")
        void shouldParseDatetimeFormat() {
            LocalDateTime result = DateUtils.parse("2024-06-15 14:30:45");
            assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30, 45), result);
        }

        @Test
        @DisplayName("parse 应解析 yyyy-MM-dd 格式并返回当天起始")
        void shouldParseDateFormat() {
            LocalDateTime result = DateUtils.parse("2024-06-15");
            assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0, 0), result);
        }

        @Test
        @DisplayName("parse 应解析 yyyyMMdd 格式")
        void shouldParseCompactDateFormat() {
            LocalDateTime result = DateUtils.parse("20240615");
            assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0, 0), result);
        }

        @Test
        @DisplayName("parse 应解析 yyyyMMddHHmmss 格式")
        void shouldParseCompactDatetimeFormat() {
            LocalDateTime result = DateUtils.parse("20240615143045");
            assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30, 45), result);
        }

        @Test
        @DisplayName("parse(null) 应返回 null")
        void shouldReturnNullForNull() {
            assertNull(DateUtils.parse(null));
        }

        @Test
        @DisplayName("parse('') 应返回 null")
        void shouldReturnNullForEmpty() {
            assertNull(DateUtils.parse(""));
        }

        @Test
        @DisplayName("parseDate 应解析日期字符串")
        void shouldParseDate() {
            LocalDate result = DateUtils.parseDate("2024-06-15");
            assertEquals(LocalDate.of(2024, 6, 15), result);
        }

        @Test
        @DisplayName("parse(text, pattern) 应使用自定义格式")
        void shouldParseWithCustomPattern() {
            LocalDateTime result = DateUtils.parse("2024/06/15 14:30:45", "yyyy/MM/dd HH:mm:ss");
            assertEquals(LocalDateTime.of(2024, 6, 15, 14, 30, 45), result);
        }
    }

    @Nested
    @DisplayName("类型转换")
    class Conversion {

        @Test
        @DisplayName("toDate(LocalDateTime) 应转换为 Date")
        void shouldConvertLocalDateTimeToDate() {
            LocalDateTime ldt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            Date date = DateUtils.toDate(ldt);
            assertNotNull(date);
            assertEquals(ldt, DateUtils.toLocalDateTime(date));
        }

        @Test
        @DisplayName("toDate(null) 应返回 null")
        void shouldReturnNullForNullConversion() {
            assertNull(DateUtils.toDate((LocalDateTime) null));
        }

        @Test
        @DisplayName("toLocalDateTime(Date) 应转换为 LocalDateTime")
        void shouldConvertDateToLocalDateTime() {
            Date date = new Date();
            LocalDateTime ldt = DateUtils.toLocalDateTime(date);
            assertNotNull(ldt);
        }

        @Test
        @DisplayName("toLocalDate(Date) 应转换为 LocalDate")
        void shouldConvertDateToLocalDate() {
            Date date = new Date();
            LocalDate ld = DateUtils.toLocalDate(date);
            assertNotNull(ld);
        }

        @Test
        @DisplayName("toEpochMilli 和 fromEpochMilli 应可逆")
        void shouldBeReversible() {
            LocalDateTime original = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            long millis = DateUtils.toEpochMilli(original);
            LocalDateTime restored = DateUtils.fromEpochMilli(millis);
            assertEquals(original, restored);
        }

        @Test
        @DisplayName("toEpochMilli(null) 应返回 0")
        void shouldReturnZeroForNull() {
            assertEquals(0, DateUtils.toEpochMilli(null));
        }
    }

    @Nested
    @DisplayName("begin/end 范围")
    class Range {

        @Test
        @DisplayName("beginOfDay 应返回当天 00:00:00")
        void shouldReturnBeginOfDay() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0, 0), DateUtils.beginOfDay(dt));
        }

        @Test
        @DisplayName("endOfDay 应返回当天 23:59:59.999999999")
        void shouldReturnEndOfDay() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals(LocalDateTime.of(2024, 6, 15, 23, 59, 59, 999999999), DateUtils.endOfDay(dt));
        }

        @Test
        @DisplayName("beginOfMonth 应返回当月第一天")
        void shouldReturnBeginOfMonth() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals(LocalDateTime.of(2024, 6, 1, 0, 0, 0), DateUtils.beginOfMonth(dt));
        }

        @Test
        @DisplayName("endOfMonth 应返回当月最后一天")
        void shouldReturnEndOfMonth() {
            LocalDateTime dt = LocalDateTime.of(2024, 2, 15, 14, 30, 45); // 闰年
            assertEquals(LocalDateTime.of(2024, 2, 29, 23, 59, 59, 999999999), DateUtils.endOfMonth(dt));
        }

        @Test
        @DisplayName("beginOfYear 应返回当年第一天")
        void shouldReturnBeginOfYear() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals(LocalDateTime.of(2024, 1, 1, 0, 0, 0), DateUtils.beginOfYear(dt));
        }

        @Test
        @DisplayName("endOfYear 应返回当年最后一天")
        void shouldReturnEndOfYear() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 14, 30, 45);
            assertEquals(LocalDateTime.of(2024, 12, 31, 23, 59, 59, 999999999), DateUtils.endOfYear(dt));
        }
    }

    @Nested
    @DisplayName("时间计算")
    class Calculation {

        @Test
        @DisplayName("betweenDays 应计算天数差")
        void shouldCalculateDaysBetween() {
            LocalDateTime start = LocalDateTime.of(2024, 6, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 15, 0, 0);
            assertEquals(14, DateUtils.betweenDays(start, end));
        }

        @Test
        @DisplayName("betweenHours 应计算小时差")
        void shouldCalculateHoursBetween() {
            LocalDateTime start = LocalDateTime.of(2024, 6, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 1, 5, 0);
            assertEquals(5, DateUtils.betweenHours(start, end));
        }

        @Test
        @DisplayName("betweenMinutes 应计算分钟差")
        void shouldCalculateMinutesBetween() {
            LocalDateTime start = LocalDateTime.of(2024, 6, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2024, 6, 1, 0, 30);
            assertEquals(30, DateUtils.betweenMinutes(start, end));
        }

        @Test
        @DisplayName("between null 参数应返回 0")
        void shouldReturnZeroForNullBetween() {
            assertEquals(0, DateUtils.betweenDays(null, LocalDateTime.now()));
            assertEquals(0, DateUtils.betweenDays(LocalDateTime.now(), null));
        }

        @Test
        @DisplayName("plusDays 应增加天数")
        void shouldPlusDays() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 0, 0);
            assertEquals(LocalDateTime.of(2024, 6, 20, 0, 0), DateUtils.plusDays(dt, 5));
        }

        @Test
        @DisplayName("minusDays 应减少天数")
        void shouldMinusDays() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 0, 0);
            assertEquals(LocalDateTime.of(2024, 6, 10, 0, 0), DateUtils.minusDays(dt, 5));
        }

        @Test
        @DisplayName("plusDays(null) 应返回 null")
        void shouldReturnNullForNullPlus() {
            assertNull(DateUtils.plusDays(null, 5));
        }
    }

    @Nested
    @DisplayName("判断方法")
    class Condition {

        @Test
        @DisplayName("isSameDay 同一天应返回 true")
        void shouldReturnTrueForSameDay() {
            LocalDateTime dt1 = LocalDateTime.of(2024, 6, 15, 8, 0);
            LocalDateTime dt2 = LocalDateTime.of(2024, 6, 15, 20, 0);
            assertTrue(DateUtils.isSameDay(dt1, dt2));
        }

        @Test
        @DisplayName("isSameDay 不同天应返回 false")
        void shouldReturnFalseForDifferentDay() {
            LocalDateTime dt1 = LocalDateTime.of(2024, 6, 15, 23, 59);
            LocalDateTime dt2 = LocalDateTime.of(2024, 6, 16, 0, 1);
            assertFalse(DateUtils.isSameDay(dt1, dt2));
        }

        @Test
        @DisplayName("isSameDay null 应返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(DateUtils.isSameDay(null, LocalDateTime.now()));
            assertFalse(DateUtils.isSameDay(LocalDateTime.now(), null));
        }
    }

    @Nested
    @DisplayName("getWeekday / getMonthOfYear / getSeasonOfYear")
    class Getters {

        @Test
        @DisplayName("getWeekday(LocalDateTime) 周一应返回 1")
        void shouldGetWeekday() {
            // 2024-06-17 是周一
            LocalDateTime dt = LocalDateTime.of(2024, 6, 17, 0, 0);
            assertEquals(1, DateUtils.getWeekday(dt));
        }

        @Test
        @DisplayName("getWeekday(null) 应返回 0")
        void shouldReturnZeroForNullWeekday() {
            assertEquals(0, DateUtils.getWeekday((LocalDateTime) null));
        }

        @Test
        @DisplayName("getMonthOfYear 应返回月份")
        void shouldGetMonthOfYear() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 0, 0);
            assertEquals(6, DateUtils.getMonthOfYear(dt));
        }

        @Test
        @DisplayName("getSeasonOfYear 应返回季度")
        void shouldGetSeasonOfYear() {
            assertEquals(1, DateUtils.getSeasonOfYear(LocalDateTime.of(2024, 2, 1, 0, 0)));
            assertEquals(2, DateUtils.getSeasonOfYear(LocalDateTime.of(2024, 5, 1, 0, 0)));
            assertEquals(3, DateUtils.getSeasonOfYear(LocalDateTime.of(2024, 8, 1, 0, 0)));
            assertEquals(4, DateUtils.getSeasonOfYear(LocalDateTime.of(2024, 11, 1, 0, 0)));
        }

        @Test
        @DisplayName("getDayOfMonth 应返回日")
        void shouldGetDayOfMonth() {
            LocalDateTime dt = LocalDateTime.of(2024, 6, 15, 0, 0);
            assertEquals(15, DateUtils.getDayOfMonth(dt));
        }
    }

    @Nested
    @DisplayName("Date 兼容方法")
    class LegacyDate {

        @Test
        @DisplayName("getWeekday(Date) 应返回 1-7")
        void shouldGetWeekdayFromDate() {
            // 2024-06-17 是周一
            Date date = Date.from(LocalDate.of(2024, 6, 17).atStartOfDay(ZoneId.systemDefault()).toInstant());
            assertEquals(1, DateUtils.getWeekday(date));
        }

        @Test
        @DisplayName("getDayOfMonth(Date) 应返回日")
        void shouldGetDayOfMonthFromDate() {
            Date date = Date.from(LocalDate.of(2024, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant());
            assertEquals(15, DateUtils.getDayOfMonth(date));
        }

        @Test
        @DisplayName("getMonthOfYear(Date) 应返回月份")
        void shouldGetMonthOfYearFromDate() {
            Date date = Date.from(LocalDate.of(2024, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant());
            assertEquals(6, DateUtils.getMonthOfYear(date));
        }

        @Test
        @DisplayName("getSeasonOfYear(Date) 应返回季度")
        void shouldGetSeasonOfYearFromDate() {
            Date date = Date.from(LocalDate.of(2024, 8, 1).atStartOfDay(ZoneId.systemDefault()).toInstant());
            assertEquals(3, DateUtils.getSeasonOfYear(date));
        }
    }
}
