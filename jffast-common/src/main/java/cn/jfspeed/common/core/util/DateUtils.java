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
package cn.jfspeed.common.core.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

/**
 * @author jffast
 */
public class DateUtils {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final String TIME_FORMAT = "HH:mm:ss";
    private static final String COMPACT_DATETIME_FORMAT = "yyyyMMddHHmmss";
    private static final String COMPACT_DATE_FORMAT = "yyyyMMdd";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME_FORMAT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);
    private static final DateTimeFormatter COMPACT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern(COMPACT_DATETIME_FORMAT);
    private static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter.ofPattern(COMPACT_DATE_FORMAT);

    private DateUtils() {
    }

    public static int getWeekday(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek == Calendar.SUNDAY) {
            return 7;
        } else {
            return dayOfWeek - 1;
        }
    }

    public static int getDayOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static int getMonthOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);
        return month + 1;
    }

    public static int getSeasonOfYear(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);
        int quarter = (month / 3) + 1;
        return quarter;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : null;
    }

    public static String format(LocalDateTime dateTime, String pattern) {
        return dateTime != null ? dateTime.format(DateTimeFormatter.ofPattern(pattern)) : null;
    }

    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    public static String formatDate(LocalDate date, String pattern) {
        return date != null ? date.format(DateTimeFormatter.ofPattern(pattern)) : null;
    }

    public static String formatTime(LocalTime time) {
        return time != null ? time.format(TIME_FORMATTER) : null;
    }

    public static String formatCompact(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(COMPACT_DATETIME_FORMATTER) : null;
    }

    public static String formatCompactDate(LocalDate date) {
        return date != null ? date.format(COMPACT_DATE_FORMATTER) : null;
    }

    public static LocalDateTime parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        text = text.trim();
        if (text.length() == DATE_FORMAT.length()) {
            return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
        }
        if (text.length() == COMPACT_DATE_FORMAT.length()) {
            return LocalDate.parse(text, COMPACT_DATE_FORMATTER).atStartOfDay();
        }
        if (text.length() == COMPACT_DATETIME_FORMAT.length()) {
            return LocalDateTime.parse(text, COMPACT_DATETIME_FORMATTER);
        }
        return LocalDateTime.parse(text, DATETIME_FORMATTER);
    }

    public static LocalDateTime parse(String text, String pattern) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text.trim(), DateTimeFormatter.ofPattern(pattern));
    }

    public static LocalDate parseDate(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(text.trim(), DATE_FORMATTER);
    }

    public static LocalDate parseDate(String text, String pattern) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        return LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern(pattern));
    }

    public static Date toDate(LocalDateTime dateTime) {
        return dateTime != null ? Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()) : null;
    }

    public static Date toDate(LocalDate date) {
        return date != null ? Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()) : null;
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date != null ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
    }

    public static LocalDate toLocalDate(Date date) {
        return date != null ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    public static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() : 0;
    }

    public static LocalDateTime fromEpochMilli(long timestamp) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
    }

    public static LocalDateTime beginOfDay(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDate().atStartOfDay() : null;
    }

    public static LocalDateTime endOfDay(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDate().atTime(23, 59, 59, 999999999) : null;
    }

    public static LocalDateTime beginOfMonth(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.withDayOfMonth(1).toLocalDate().atStartOfDay() : null;
    }

    public static LocalDateTime endOfMonth(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.withDayOfMonth(dateTime.toLocalDate().lengthOfMonth()).toLocalDate().atTime(23, 59, 59, 999999999) : null;
    }

    public static LocalDateTime beginOfYear(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.withDayOfYear(1).toLocalDate().atStartOfDay() : null;
    }

    public static LocalDateTime endOfYear(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.withMonth(12).withDayOfMonth(31).toLocalDate().atTime(23, 59, 59, 999999999) : null;
    }

    public static long betweenDays(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null ? ChronoUnit.DAYS.between(start, end) : 0;
    }

    public static long betweenHours(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null ? ChronoUnit.HOURS.between(start, end) : 0;
    }

    public static long betweenMinutes(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null ? ChronoUnit.MINUTES.between(start, end) : 0;
    }

    public static LocalDateTime plusDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.plusDays(days) : null;
    }

    public static LocalDateTime minusDays(LocalDateTime dateTime, long days) {
        return dateTime != null ? dateTime.minusDays(days) : null;
    }

    public static boolean isSameDay(LocalDateTime dt1, LocalDateTime dt2) {
        return dt1 != null && dt2 != null && dt1.toLocalDate().equals(dt2.toLocalDate());
    }

    public static boolean isExpired(LocalDateTime dateTime) {
        return dateTime != null && dateTime.isBefore(LocalDateTime.now());
    }

    public static int getWeekday(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.getDayOfWeek().getValue() : 0;
    }

    public static int getDayOfMonth(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.getDayOfMonth() : 0;
    }

    public static int getMonthOfYear(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.getMonthValue() : 0;
    }

    public static int getSeasonOfYear(LocalDateTime dateTime) {
        return dateTime != null ? (dateTime.getMonthValue() - 1) / 3 + 1 : 0;
    }
}
