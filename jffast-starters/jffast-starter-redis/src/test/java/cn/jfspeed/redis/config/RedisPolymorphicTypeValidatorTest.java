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
package cn.jfspeed.redis.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis 值多态反序列化白名单的回归测试。
 *
 * <p>钉住两条不变量：
 * <ol>
 *   <li>Base 实际写入 Redis 的类型（字符串 / 包装类型 / 集合容器 / 本项目自有类型）
 *       必须能原样序列化并读回；</li>
 *   <li>第三方类型（Jackson 反序列化 gadget 的常见落点）必须**被拒绝**，
 *       而不是被当作 Object 静默放行。</li>
 * </ol>
 *
 * @author jffast
 */
class RedisPolymorphicTypeValidatorTest {

    private ObjectMapper serializer() {
        return JsonMapper.builder()
                .changeDefaultVisibility(v -> v.withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
                .activateDefaultTyping(RedisPolymorphicTypeValidator.INSTANCE, DefaultTyping.NON_FINAL)
                .build();
    }

    @Test
    @DisplayName("字符串与包装类型可原样往返")
    void primitivesRoundTrip() {
        ObjectMapper mapper = serializer();
        assertEquals("hello", mapper.readValue(mapper.writeValueAsString("hello"), Object.class));
        assertEquals(42, mapper.readValue(mapper.writeValueAsString(42), Object.class));
        assertEquals(Boolean.TRUE, mapper.readValue(mapper.writeValueAsString(Boolean.TRUE), Object.class));

        // Long 是 final 类型，DefaultTyping.NON_FINAL 不给它打类型标记，
        // 于是读回时按 JSON 的自然类型解析成 Integer（**既有行为**，与本次白名单无关）。
        // 业务侧的读法都是 .toString() + parseLong/parseInt，不依赖具体数值类型。
        Object longBack = mapper.readValue(mapper.writeValueAsString(123L), Object.class);
        assertTrue(longBack instanceof Number, "数值应读回为 Number，实际：" + longBack.getClass());
        assertEquals(123L, ((Number) longBack).longValue());
    }

    @Test
    @DisplayName("集合容器（权限列表 / 字典缓存）可原样往返")
    void collectionsRoundTrip() {
        ObjectMapper mapper = serializer();
        List<String> perms = new ArrayList<>(List.of("system:user:view", "system:role:view"));
        Object back = mapper.readValue(mapper.writeValueAsString(perms), Object.class);
        assertTrue(back instanceof List, "集合应读回为 List，实际：" + back.getClass());
        assertEquals(perms, back);

        Map<String, Object> map = new HashMap<>();
        map.put("errorNum", 1);
        map.put("loginTime", 1700000000000L);
        Object mapBack = mapper.readValue(mapper.writeValueAsString(map), Object.class);
        assertTrue(mapBack instanceof Map, "Map 应读回为 Map，实际：" + mapBack.getClass());
    }

    @Test
    @DisplayName("本项目自有类型在白名单内")
    void projectTypesAllowed() {
        assertTrue(RedisPolymorphicTypeValidator.isAllowedTypeName("cn.jfspeed.system.entity.SysDict"));
        assertTrue(RedisPolymorphicTypeValidator.isAllowedTypeName("cn.jfspeed.common.core.base.BaseEntity"));
    }

    @Test
    @DisplayName("第三方 gadget 类型一律拒绝")
    void thirdPartyTypesDenied() {
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName("org.apache.commons.collections.functors.InvokerTransformer"));
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName("org.springframework.beans.factory.ObjectFactory"));
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName("java.lang.ProcessBuilder"));
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName("javax.naming.InitialContext"));
    }

    @Test
    @DisplayName("类型 id 指向白名单外的类时反序列化必须失败（失败关闭，不得静默放行）")
    void maliciousTypeIdIsRejected() {
        ObjectMapper mapper = serializer();
        // 模拟攻击者投递的类型 id：要求按该类反序列化
        String payload = "[\"org.apache.commons.collections.functors.InvokerTransformer\",{}]";
        assertThrows(Exception.class, () -> mapper.readValue(payload, Object.class),
                "白名单外的类型 id 必须导致反序列化失败");
    }

    @Test
    @DisplayName("空白与 null 类型名一律拒绝")
    void blankTypeNamesDenied() {
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName(null));
        assertFalse(RedisPolymorphicTypeValidator.isAllowedTypeName(""));
    }

    @Test
    @DisplayName("带类型标记的 JDK 只读/定长容器壳必须放行（漏一个就是缓存读回直接失败）")
    void readOnlyCollectionShellsRoundTrip() {
        ObjectMapper mapper = serializer();
        Map<String, Object> samples = new LinkedHashMap<>();
        samples.put("Arrays.asList", java.util.Arrays.asList("a", "b"));
        samples.put("Collections.singletonList", java.util.Collections.singletonList("a"));
        samples.put("Collections.unmodifiableList",
                java.util.Collections.unmodifiableList(new ArrayList<>(List.of("a", "b"))));
        samples.put("Collections.unmodifiableSet",
                java.util.Collections.unmodifiableSet(new java.util.HashSet<>(List.of("a"))));
        samples.put("Collections.unmodifiableMap",
                java.util.Collections.unmodifiableMap(new HashMap<>(Map.of("k", "v"))));
        List<String> failed = new ArrayList<>();
        samples.forEach((name, sample) -> {
            try {
                Object back = mapper.readValue(mapper.writeValueAsString(sample), Object.class);
                if (!java.util.Objects.equals(back, sample)) {
                    failed.add(name + " 读回不相等：" + back);
                }
            } catch (Exception e) {
                failed.add(name + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
        assertTrue(failed.isEmpty(), "以下容器无法往返：\n" + String.join("\n", failed));
    }

    @Test
    @DisplayName("JDK 不可变集合（List.of/Set.of/Map.of/toList）本就不写类型标记：既有行为，钉住以免误判成白名单回归")
    void immutableJdkCollectionsNeverRoundTrip() {
        ObjectMapper mapper = serializer();
        // 这些值在 Jackson 3 的 NON_FINAL 默认类型下序列化成「裸」JSON 数组/对象（没有类型 id），
        // 以 Object 读回必然失败。实测：把校验器换成放行全部同样失败 ⇒ 与白名单无关，
        // 是 DefaultTyping 的既有限制。业务侧要缓存集合请新建可变容器（见类注释）。
        for (Object sample : List.of(List.of("a"), java.util.Set.of(1, 2), java.util.Map.of("k", "v"))) {
            String json = mapper.writeValueAsString(sample);
            assertFalse(json.contains(sample.getClass().getName()),
                    sample.getClass() + " 不应写出类型标记，实际 JSON：" + json);
            assertThrows(Exception.class, () -> mapper.readValue(json, Object.class),
                    sample.getClass() + " 以 Object 读回应失败");
        }
    }
}
