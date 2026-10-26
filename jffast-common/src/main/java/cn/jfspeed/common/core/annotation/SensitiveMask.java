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
package cn.jfspeed.common.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段脱敏注解。
 * <p>
 * 可标注在字段上，由 Jackson 序列化器、Excel 导出、日志等统一识别并进行脱敏处理。
 *
 * @author jffast
 */
@Documented
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveMask {

    MaskType type() default MaskType.MIDDLE_MASK;

    int prefixKeep() default 1;

    int suffixKeep() default 1;

    int maskLength() default 0;

    enum MaskType {
        ALL_MASK,
        MIDDLE_MASK,
        PREFIX_MASK,
        SUFFIX_MASK
    }
}
