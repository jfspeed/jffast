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

import java.util.Date;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code spring.jackson.*} 前缀在 Spring Boot 4（Jackson 3）下的绑定契约。
 * <p>
 * 只加载 {@link JacksonAutoConfiguration}，不引入项目自身的 {@link JacksonGlobalConfig}，
 * 以便单独观察 Boot 原生属性是否仍然生效。三类结论各一条用例：
 * <ul>
 * <li>仍在生效：{@code date-format} / {@code time-zone} / {@code default-property-inclusion}</li>
 * <li>已被移除且静默忽略：{@code parser.*} / {@code generator.*}（改为 {@code read.*} / {@code write.*}）</li>
 * <li>语义已变：Jackson 2 的 {@code serialization.write-dates-as-timestamps} 常量在 Jackson 3 已移出
 *     {@code SerializationFeature}，绑定直接失败（不再静默）</li>
 * </ul>
 *
 * @author jffast
 */
@DisplayName("spring.jackson.* 前缀在 Boot 4 下的绑定契约")
class JacksonPropertiesContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class));

    static class Sample {
        public String name;
        public String nil;
        public Date createdAt;

        Sample(String name, String nil, Date createdAt) {
            this.name = name;
            this.nil = nil;
            this.createdAt = createdAt;
        }
    }

    @Test
    @DisplayName("date-format / time-zone / default-property-inclusion 仍然生效")
    void commonPrefixesStillBindAndTakeEffect() {
        runner.withPropertyValues(
                        "spring.jackson.default-property-inclusion=non_null",
                        "spring.jackson.date-format=yyyy-MM-dd HH:mm:ss",
                        "spring.jackson.time-zone=GMT+8")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JacksonProperties properties = context.getBean(JacksonProperties.class);
                    assertThat(properties.getDateFormat()).isEqualTo("yyyy-MM-dd HH:mm:ss");
                    assertThat(properties.getDefaultPropertyInclusion()).isEqualTo(JsonInclude.Include.NON_NULL);
                    assertThat(properties.getTimeZone()).isEqualTo(TimeZone.getTimeZone("GMT+8"));

                    JsonMapper mapper = context.getBean(JsonMapper.class);
                    String json = mapper.writeValueAsString(new Sample("a", null, new Date(0)));
                    assertThat(json)
                            .contains("\"name\":\"a\"")
                            .contains("\"createdAt\":\"1970-01-01 08:00:00\"")
                            .doesNotContain("nil");
                });
    }

    @Test
    @DisplayName("parser.* / generator.* 已移除，配置被静默忽略")
    void removedParserAndGeneratorPrefixesAreSilentlyIgnored() {
        runner.withPropertyValues(
                        "spring.jackson.parser.allow-single-quotes=true",
                        "spring.jackson.generator.escape-non-ascii=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JsonMapper mapper = context.getBean(JsonMapper.class);
                    assertThatThrownBy(() -> mapper.readTree("{'a':1}"))
                            .as("parser.allow-single-quotes 已无对应属性，单引号仍应按默认拒绝")
                            .isInstanceOf(Exception.class);
                });
    }

    @Test
    @DisplayName("serialization.write-dates-as-timestamps 在 Jackson 3 已不存在，绑定即失败")
    void movedSerializationFeatureFailsFast() {
        runner.withPropertyValues("spring.jackson.serialization.write-dates-as-timestamps=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("write-dates-as-timestamps");
                });
    }

}
