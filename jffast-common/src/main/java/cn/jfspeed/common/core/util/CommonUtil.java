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

import cn.hutool.core.date.LocalDateTimeUtil;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * @author jffast
 */
public class CommonUtil {
    /**
     * 截取固定长度字符*
     * @param str 原始字符*
     * @param length 截取长度
     * @return 截取后的字符*
     */
    public static String substring(String str, int length) {
        if (str == null || str.length() <= length) {
            return str;
        }
        return str.substring(0, length);
    }

    /**
     * 将异常的详细信息（包括堆栈跟踪）转换为字符串
     */
    public static String getExceptionDetails(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // 打印异常堆栈信息到PrintWriter
        e.printStackTrace(pw);

        // 关闭资源
        pw.close();

        // 返回包含异常信息的字符串
        return sw.toString();
    }
    /**
     * 根据生日计算精确年龄（精确到小数*
     * @param birthday 生日，格式为yyyy-MM-dd
     * @return 精确到小数的年龄
     */
    public static double calculatePreciseAge(String birthday) {
        // 解析生日字符串为LocalDate对象
        LocalDate birthDate = LocalDateTimeUtil.parseDate(birthday);
        if (birthDate == null) {
            throw new IllegalArgumentException("生日格式不正确，请使用yyyy-MM-dd格式");
        }

        // 获取当前日期
        LocalDate today = LocalDate.now();

        // 计算从生日到今天的总天*
        long days = ChronoUnit.DAYS.between(birthDate, today);

        // 计算精确年龄（一年按365.2425天计算，考虑闰年*
        return days / 365.2425;
    }

}
