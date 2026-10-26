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
package cn.jfspeed.web.config.serializer;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import cn.jfspeed.common.core.annotation.SensitiveMask;
import cn.jfspeed.common.core.util.SensitiveMaskProcessor;

/**
 * 敏感字段脱敏序列化器（Jackson 3）。
 * <p>
 * Jackson 3 中 {@code ContextualSerializer} 接口已并入 {@link ValueSerializer} 基类，
 * 通过覆写 {@link #createContextual(SerializationContext, BeanProperty)} 实现按注解参数定制。
 *
 * @author jffast
 */
public class SensitiveMaskSerializer extends ValueSerializer<String> {

    private SensitiveMask.MaskType maskType = SensitiveMask.MaskType.MIDDLE_MASK;
    private int prefixKeep = 1;
    private int suffixKeep = 1;
    private int maskLength = 0;

    public SensitiveMaskSerializer() {
    }

    public SensitiveMaskSerializer(SensitiveMask.MaskType maskType, int prefixKeep, int suffixKeep, int maskLength) {
        this.maskType = maskType;
        this.prefixKeep = prefixKeep;
        this.suffixKeep = suffixKeep;
        this.maskLength = maskLength;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null || value.isEmpty()) {
            gen.writeString(value);
            return;
        }
        SensitiveMask annotation = new SensitiveMask() {
            @Override
            public Class<SensitiveMask> annotationType() {
                return SensitiveMask.class;
            }

            @Override
            public MaskType type() {
                return maskType;
            }

            @Override
            public int prefixKeep() {
                return prefixKeep;
            }

            @Override
            public int suffixKeep() {
                return suffixKeep;
            }

            @Override
            public int maskLength() {
                return maskLength;
            }
        };
        gen.writeString(SensitiveMaskProcessor.mask(value, annotation));
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext prov, BeanProperty property) {
        if (property != null) {
            SensitiveMask annotation = property.getAnnotation(SensitiveMask.class);
            if (annotation != null) {
                return new SensitiveMaskSerializer(annotation.type(), annotation.prefixKeep(), annotation.suffixKeep(), annotation.maskLength());
            }
        }
        return null;
    }
}
