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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Web Starter 配置属性
 *
 * <pre>
 * jffast:
 *   web:
 *     trace-id:
 *       enabled: true        # 是否启用 TraceId（默认 true）
 *     xss:
 *       enabled: true        # 是否启用 XSS 过滤（默认 true）
 *       exclude-urls: []     # XSS 排除的 URL 列表（精确匹配或 * 前缀匹配）
 *       json-clean: true     # 是否清洗 JSON 请求体（默认 true）
 *       json-skip-fields: [bpmnXml, contentTemplate]   # JSON 清洗豁免字段名
 * </pre>
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.web")
public class WebProperties {

    /** TraceId 链路追踪配置 */
    private TraceId traceId = new TraceId();

    /** XSS 过滤配置 */
    private Xss xss = new Xss();

    /**
     * TraceId 链路追踪配置
     */
    @Data
    public static class TraceId {
        /** 是否启用 TraceId，默认 true */
        private boolean enabled = true;
    }

    /**
     * XSS 过滤配置
     */
    @Data
    public static class Xss {
        /** 是否启用 XSS 过滤，默认 true */
        private boolean enabled = true;
        /** XSS 过滤排除的 URL 列表（精确匹配或 * 前缀匹配） */
        private List<String> excludeUrls = new ArrayList<>();
        /** 是否清洗 JSON 请求体，默认 true */
        private boolean jsonClean = true;
        /** JSON 清洗豁免的属性名（携带代码/模板的字段，如 bpmnXml、contentTemplate） */
        private List<String> jsonSkipFields = new ArrayList<>(List.of("bpmnXml", "contentTemplate"));

        /** 豁免字段名集合（供过滤器匹配用） */
        public Set<String> getJsonSkipFieldsSet() {
            return new HashSet<>(jsonSkipFields);
        }
    }
}
