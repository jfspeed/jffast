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
package cn.jfspeed.web.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * XSS 过滤工具类。
 * <p>
 * 供 {@link cn.jfspeed.web.filter.XssHttpServletRequestWrapper} 使用：
 * 对 form/query/header 参数以及 JSON 请求体中的字符串值做轻量级 HTML 实体转义。
 * </p>
 *
 * @author jffast
 */
public class XssUtil {

    /** 仅用于 JSON 解析/重写，不带任何业务序列化定制 */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private XssUtil() {
    }

    /**
     * 清理字符串中的 XSS 风险字符，替换为 HTML 实体。
     *
     * <p>仅转义 {@code < > &}，保留 {@code " ' ( )} 等正常字符，防止过度转义破坏业务数据。</p>
     *
     * @param value 原始字符串
     * @return 清理后的字符串；null/空串原样返回
     */
    public static String clean(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        value = value.replace("&", "&amp;");
        value = value.replace("<", "&lt;");
        value = value.replace(">", "&gt;");

        return value;
    }

    /**
     * 清理 JSON 请求体中的 XSS 风险字符：递归清洗所有字符串值（含 {@code <} 形式的
     * Unicode 转义，因为先解析成树再清洗，绕过转义写法同样会被清洗到）。
     *
     * <p>豁免字段按**属性名**匹配（如携带代码/模板的 {@code bpmnXml}、{@code contentTemplate}），
     * 整个值跳过清洗，避免破坏 XML/模板内容。</p>
     *
     * <p>非法 JSON 原样返回：由 Spring 消息转换器报 400，不在过滤器里截胡。</p>
     *
     * @param json       原始 JSON 文本
     * @param skipFields 豁免的属性名集合（可为 null）
     * @return 清理后的 JSON 文本
     */
    public static String cleanJson(String json, Set<String> skipFields) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        Set<String> skip = skipFields == null ? Collections.emptySet() : skipFields;
        try {
            JsonNode root = JSON_MAPPER.readTree(json);
            return JSON_MAPPER.writeValueAsString(cleanNode(root, skip));
        } catch (JacksonException e) {
            return json;
        }
    }

    /** 递归清洗 JSON 树中的文本节点，返回（可能替换过的）节点 */
    private static JsonNode cleanNode(JsonNode node, Set<String> skipFields) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> entry : obj.properties()) {
                if (skipFields.contains(entry.getKey())) {
                    continue;
                }
                entry.setValue(cleanNode(entry.getValue(), skipFields));
            }
            return node;
        }
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, cleanNode(arr.get(i), skipFields));
            }
            return node;
        }
        if (node.isTextual()) {
            String text = node.textValue();
            String cleaned = clean(text);
            return cleaned.equals(text) ? node : StringNode.valueOf(cleaned);
        }
        return node;
    }
}
