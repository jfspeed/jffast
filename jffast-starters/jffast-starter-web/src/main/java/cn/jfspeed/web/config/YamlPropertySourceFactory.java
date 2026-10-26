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

import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Map;

/**
 * 让 {@code @PropertySource} 能加载 YAML（Spring 默认只认 properties）。
 * 没有它时，starter 自带的 application-web-*.yml 会被当 properties 解析、层级丢失而静默失效。
 *
 * @author jffast
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        if (!resource.getResource().exists()) {
            throw new IllegalArgumentException("Resource not found: " + resource.getResource());
        }
        org.springframework.boot.env.YamlPropertySourceLoader loader =
                new org.springframework.boot.env.YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(name, resource.getResource());
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Failed to load YAML from " + resource.getResource());
        }
        if (sources.size() == 1) {
            return sources.get(0);
        }
        Properties merged = new Properties();
        for (PropertySource<?> ps : sources) {
            Object src = ps.getSource();
            if (src instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        merged.setProperty(String.valueOf(k), String.valueOf(v));
                    }
                });
            } else if (src instanceof Properties props) {
                merged.putAll(props);
            }
        }
        return new PropertiesPropertySource(name, merged);
    }
}
