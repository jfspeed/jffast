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
package cn.jfspeed.web.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * 全局 Jackson 定制（Jackson 3 / Spring Boot 4）。
 * <p>
 * 序列化契约（前端依赖，改动需同步前端）：
 * <ul>
 * <li>LocalDateTime / LocalDate / LocalTime 统一格式化输出；反序列化同时兼容 ISO 与自定义格式</li>
 * <li>Long 超出 JS 安全整数范围（±2^53-1）时序列化为字符串，防止前端精度丢失</li>
 * </ul>
 *
 * @author jffast
 */
@Configuration
public class JacksonGlobalConfig {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final long MAX_SAFE_INTEGER = 9007199254740991L;
    public static final long MIN_SAFE_INTEGER = -9007199254740991L;

    @Bean
    public JsonMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new StdSerializer<>(LocalDateTime.class) {
                @Override
                public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                    gen.writeString(value.format(DATETIME_FORMATTER));
                }
            });
            module.addDeserializer(LocalDateTime.class, new StdDeserializer<>(LocalDateTime.class) {
                @Override
                public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
                    String text = p.getString().trim();
                    if (text.isEmpty()) {
                        return null;
                    }
                    if (text.contains("T")) {
                        return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    return LocalDateTime.parse(text, DATETIME_FORMATTER);
                }
            });
            module.addSerializer(LocalDate.class, new StdSerializer<>(LocalDate.class) {
                @Override
                public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) {
                    gen.writeString(value.format(DATE_FORMATTER));
                }
            });
            module.addDeserializer(LocalDate.class, new StdDeserializer<>(LocalDate.class) {
                @Override
                public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
                    String text = p.getString().trim();
                    if (text.isEmpty()) {
                        return null;
                    }
                    return LocalDate.parse(text, DATE_FORMATTER);
                }
            });
            module.addSerializer(LocalTime.class, new StdSerializer<>(LocalTime.class) {
                @Override
                public void serialize(LocalTime value, JsonGenerator gen, SerializationContext ctxt) {
                    gen.writeString(value.format(TIME_FORMATTER));
                }
            });
            module.addDeserializer(LocalTime.class, new StdDeserializer<>(LocalTime.class) {
                @Override
                public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) {
                    String text = p.getString().trim();
                    if (text.isEmpty()) {
                        return null;
                    }
                    return LocalTime.parse(text, TIME_FORMATTER);
                }
            });
            module.addSerializer(Long.class, new StdSerializer<>(Long.class) {
                @Override
                public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) {
                    if (value == null) {
                        gen.writeNull();
                        return;
                    }
                    if (value > MAX_SAFE_INTEGER || value < MIN_SAFE_INTEGER) {
                        gen.writeString(value.toString());
                    } else {
                        gen.writeNumber(value);
                    }
                }
            });
            builder.addModule(module);
        };
    }
}
