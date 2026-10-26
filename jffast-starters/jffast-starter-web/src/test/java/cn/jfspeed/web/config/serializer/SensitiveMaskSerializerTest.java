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

import cn.jfspeed.common.core.annotation.SensitiveMask;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SensitiveMaskSerializer 测试：脱敏序列化、空值直写、createContextual 上下文解析。
 *
 * @author jffast
 */
@DisplayName("SensitiveMaskSerializer 脱敏序列化测试")
class SensitiveMaskSerializerTest {

    static class MaskedBean {
        @JsonSerialize(using = SensitiveMaskSerializer.class)
        @SensitiveMask(type = SensitiveMask.MaskType.ALL_MASK, maskLength = 4)
        public String secret = "password123";
    }

    static class MiddleBean {
        @JsonSerialize(using = SensitiveMaskSerializer.class)
        @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 1, suffixKeep = 1)
        public String phone = "13812345678";
    }

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("按注解配置全量打码")
    void shouldMaskAll() {
        String json = mapper.writeValueAsString(new MaskedBean());
        assertEquals("{\"secret\":\"****\"}", json);
    }

    @Test
    @DisplayName("按注解配置中间打码并保留首尾")
    void shouldMaskMiddle() {
        String json = mapper.writeValueAsString(new MiddleBean());
        assertEquals("{\"phone\":\"1*********8\"}", json);
    }

    @Test
    @DisplayName("null 与空串原样写出，不脱敏")
    void shouldWriteNullAndEmptyAsIs() {
        SensitiveMaskSerializer serializer = new SensitiveMaskSerializer();
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = mapper.createGenerator(sw)) {
            serializer.serialize(null, gen, null);
        }
        assertEquals("null", sw.toString());

        sw = new StringWriter();
        try (JsonGenerator gen = mapper.createGenerator(sw)) {
            serializer.serialize("", gen, null);
        }
        assertEquals("\"\"", sw.toString());
    }

    @Test
    @DisplayName("createContextual 应识别属性上的注解并构造带配置的序列化器")
    void shouldCreateContextualFromAnnotation() throws Exception {
        SensitiveMask annotation = MaskedBean.class.getField("secret").getAnnotation(SensitiveMask.class);
        BeanProperty property = mock(BeanProperty.class);
        when(property.getAnnotation(SensitiveMask.class)).thenReturn(annotation);

        ValueSerializer<?> result = new SensitiveMaskSerializer().createContextual(null, property);
        assertInstanceOf(SensitiveMaskSerializer.class, result);
    }

    @Test
    @DisplayName("属性无注解或属性为空应返回 null")
    void shouldReturnNullWithoutAnnotation() {
        BeanProperty bare = mock(BeanProperty.class);
        when(bare.getAnnotation(SensitiveMask.class)).thenReturn(null);
        assertNull(new SensitiveMaskSerializer().createContextual(null, bare));
        assertNull(new SensitiveMaskSerializer().createContextual(null, null));
    }
}
