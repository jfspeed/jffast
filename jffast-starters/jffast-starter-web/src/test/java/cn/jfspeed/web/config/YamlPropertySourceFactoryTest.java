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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YamlPropertySourceFactory 测试：yml 属性源加载、多文档合并、缺失资源报错。
 *
 * @author jffast
 */
@DisplayName("YamlPropertySourceFactory YAML 属性源加载测试")
class YamlPropertySourceFactoryTest {

    private final YamlPropertySourceFactory factory = new YamlPropertySourceFactory();

    private EncodedResource resourceOf(String yaml) {
        return new EncodedResource(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getDescription() {
                return "test-yaml";
            }
        });
    }

    @Test
    @DisplayName("应把单文档 yml 加载为属性源")
    void shouldLoadSingleDocument() throws Exception {
        PropertySource<?> source = factory.createPropertySource("test", resourceOf("web:\n  title: jffast\n"));
        assertNotNull(source);
        assertEquals("jffast", source.getProperty("web.title"));
    }

    @Test
    @DisplayName("应把多文档 yml 合并为一个属性源")
    void shouldMergeMultiDocument() throws Exception {
        String yaml = "a: 1\n---\nb: 2\n";
        PropertySource<?> source = factory.createPropertySource("test", resourceOf(yaml));
        assertNotNull(source);
        assertEquals("1", source.getProperty("a"));
        assertEquals("2", source.getProperty("b"));
    }

    @Test
    @DisplayName("资源不存在应抛 IllegalArgumentException")
    void shouldRejectMissingResource() {
        EncodedResource missing = new EncodedResource(new ClassPathResource("no-such-file-for-test.yml"));
        assertThrows(IllegalArgumentException.class, () -> factory.createPropertySource("test", missing));
    }

    @Test
    @DisplayName("空 yml 加载不出属性源应抛 IllegalArgumentException")
    void shouldRejectEmptyYaml() {
        assertThrows(IllegalArgumentException.class, () -> factory.createPropertySource("test", resourceOf("")));
    }

    @Test
    @DisplayName("合并结果应为 PropertiesPropertySource（含全部键值）")
    void mergedSourceShouldContainAllEntries() throws Exception {
        PropertySource<?> source = factory.createPropertySource("test", resourceOf("x: 1\n---\ny: 2\n---\nz: 3\n"));
        Object src = source.getSource();
        assertInstanceOf(Properties.class, src);
        Properties props = (Properties) src;
        assertEquals(3, props.size());
        assertEquals("1", props.getProperty("x"));
        assertEquals("2", props.getProperty("y"));
        assertEquals("3", props.getProperty("z"));
    }
}
