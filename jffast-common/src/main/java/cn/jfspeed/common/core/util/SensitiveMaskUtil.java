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

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

import cn.jfspeed.common.core.annotation.SensitiveMask;

/**
 * 敏感字段统一脱敏入口。
 *
 * <p>支持：</p>
 * <ul>
 *     <li>对象递归脱敏：扫描字段上的 {@link SensitiveMask} 注解</li>
 *     <li>字符串按规则脱敏</li>
 *     <li>文本正则脱敏：手机号、身份证号、邮箱、密码/密钥/token 等</li>
 * </ul>
 *
 * @author jffast
 */
public class SensitiveMaskUtil {

    private SensitiveMaskUtil() {
    }

    private static final Pattern MOBILE_PATTERN = Pattern.compile("(1[3-9])\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("([1-9]\\d{5})(\\d{4})(\\d{4})(\\d{3}[\\dXx])");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([\\w.+-]{1,2})[\\w.+-]*@([\\w-]+(?:\\.[\\w-]+)+)");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd|secret|token|key|authorization|accessKey|secretKey)\\s*[:=]\\s*[\"']?([^\"'\\s,;]+)[\"']?");

    /**
     * 递归脱敏对象。
     *
     * <p>仅处理当前对象自身的字段，不创建新对象。对 Map/Collection/Array 会递归处理元素。</p>
     *
     * @param target 目标对象
     */
    public static void mask(Object target) {
        if (target == null) {
            return;
        }
        if (target instanceof String) {
            return;
        }
        if (target instanceof Collection<?>) {
            ((Collection<?>) target).forEach(SensitiveMaskUtil::mask);
            return;
        }
        if (target.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(target);
            for (int i = 0; i < length; i++) {
                mask(java.lang.reflect.Array.get(target, i));
            }
            return;
        }
        if (target instanceof Map<?, ?>) {
            ((Map<?, ?>) target).values().forEach(SensitiveMaskUtil::mask);
            return;
        }

        Class<?> clazz = target.getClass();
        // 跳过 JDK 内置类型与基本类型包装类
        if (clazz.getName().startsWith("java.") || clazz.isPrimitive()) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            SensitiveMask annotation = field.getAnnotation(SensitiveMask.class);
            if (annotation == null) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(target);
                if (value instanceof String str && !str.isEmpty()) {
                    field.set(target, SensitiveMaskProcessor.mask(str, annotation));
                }
            } catch (IllegalAccessException ignored) {
                // 忽略无权限访问的字段
            }
        }
    }

    /**
     * 按类型脱敏字符串。
     */
    public static String mask(String value, SensitiveMask.MaskType type) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        SensitiveMask annotation = new SensitiveMask() {
            @Override
            public MaskType type() {
                return type;
            }

            @Override
            public int prefixKeep() {
                return 1;
            }

            @Override
            public int suffixKeep() {
                return 1;
            }

            @Override
            public int maskLength() {
                return 0;
            }

            @Override
            public Class<SensitiveMask> annotationType() {
                return SensitiveMask.class;
            }
        };
        return SensitiveMaskProcessor.mask(value, annotation);
    }

    /**
     * 对文本中的常见敏感信息进行正则脱敏。
     *
     * <p>覆盖：手机号、身份证号、邮箱、密码/密钥/token 等键值对。</p>
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 先匹配身份证号，避免其中的数字段被手机号正则误命中
        String result = ID_CARD_PATTERN.matcher(text).replaceAll(match ->
                match.group(1) + "********" + match.group(4));
        result = MOBILE_PATTERN.matcher(result).replaceAll(match -> {
            String g = match.group();
            return g.substring(0, 3) + "****" + g.substring(7);
        });
        result = EMAIL_PATTERN.matcher(result).replaceAll(match ->
                match.group(1) + "****@" + match.group(2));
        result = PASSWORD_PATTERN.matcher(result).replaceAll(match ->
                match.group(1) + "=\"****\"");
        return result;
    }
}
