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

import cn.jfspeed.common.core.annotation.SensitiveMask;

/**
 * 敏感字段脱敏处理器。
 * <p>
 * 根据 {@link SensitiveMask} 注解配置对字符串进行脱敏，供 Jackson、Excel、日志等复用。
 *
 * @author jffast
 */
public class SensitiveMaskProcessor {

    private SensitiveMaskProcessor() {
    }

    /**
     * 按注解规则脱敏。
     *
     * @param value      原始值
     * @param annotation 脱敏注解
     * @return 脱敏后的值
     */
    public static String mask(String value, SensitiveMask annotation) {
        if (value == null || value.isEmpty() || annotation == null) {
            return value;
        }

        int len = value.length();
        int prefixKeep = annotation.prefixKeep();
        int suffixKeep = annotation.suffixKeep();
        int maskLength = annotation.maskLength();

        switch (annotation.type()) {
            case ALL_MASK:
                return repeat("*", maskLength > 0 ? maskLength : len);
            case MIDDLE_MASK: {
                if (len <= prefixKeep + suffixKeep) {
                    return repeat("*", len);
                }
                int maskLen = maskLength > 0 ? maskLength : len - prefixKeep - suffixKeep;
                return value.substring(0, prefixKeep) + repeat("*", maskLen) + value.substring(len - suffixKeep);
            }
            case PREFIX_MASK: {
                int keep = suffixKeep > 0 ? suffixKeep : 1;
                if (len <= keep) {
                    return repeat("*", len);
                }
                int maskLen = maskLength > 0 ? maskLength : len - keep;
                return repeat("*", maskLen) + value.substring(len - keep);
            }
            case SUFFIX_MASK: {
                int keep = prefixKeep > 0 ? prefixKeep : 1;
                if (len <= keep) {
                    return repeat("*", len);
                }
                int maskLen = maskLength > 0 ? maskLength : len - keep;
                return value.substring(0, keep) + repeat("*", maskLen);
            }
            default:
                return value;
        }
    }

    private static String repeat(String ch, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(ch);
        }
        return sb.toString();
    }
}
