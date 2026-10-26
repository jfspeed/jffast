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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 自动配置的装配回归测试（无需真实 Redis：连接工厂用 mock，本测试不发起任何命令）。
 *
 * <p>钉住三件事：
 * <ol>
 *   <li>键序列化是字符串、值序列化带类型标记，且<b>白名单真的挂在装配出来的序列化器上</b>
 *       （{@code RedisPolymorphicTypeValidatorTest} 只证明校验器本身正确，不证明它被用上）；</li>
 *   <li>缓存管理器与 RedisTemplate 能被装配出来（防止 bean 缺失只在运行时暴露）；</li>
 *   <li>{@code wiselyKeyGenerator} 对 null 入参不抛 NPE。</li>
 * </ol>
 *
 * @author jffast
 */
class JfFastRedisAutoConfigurationTest {

    private final JfFastRedisAutoConfiguration configuration = new JfFastRedisAutoConfiguration();

    private RedisTemplate<String, Object> wiredTemplate() {
        return configuration.redisTemplate(mock(RedisConnectionFactory.class));
    }

    @Test
    @DisplayName("键用字符串序列化；值序列化器能原样往返本项目写入的集合")
    @SuppressWarnings("unchecked")
    void serializersAreWired() {
        RedisTemplate<String, Object> template = wiredTemplate();
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer(), "键必须是字符串序列化");
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer(), "哈希键必须是字符串序列化");

        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) template.getValueSerializer();
        ArrayList<String> perms = new ArrayList<>(List.of("system:user:view", "system:role:view"));
        List<?> back = (List<?>) valueSerializer.deserialize(valueSerializer.serialize(perms));
        assertEquals(perms, back, "权限列表应原样读回，实际：" + back);
    }

    @Test
    @DisplayName("装配出来的值序列化器必须拒绝白名单外的类型 id（否则白名单只是摆设）")
    void valueSerializerDeniesThirdPartyTypeId() {
        RedisSerializer<Object> valueSerializer = valueSerializerOf();
        byte[] payload = "[\"org.apache.commons.collections.functors.InvokerTransformer\",{}]"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> valueSerializer.deserialize(payload),
                "第三方 gadget 类型 id 必须让反序列化失败，而不是静默当作 Object");
    }

    @Test
    @DisplayName("哈希值序列化与值序列化同口径（同一份白名单）")
    void hashValueSerializerUsesTheSameWhitelist() {
        RedisSerializer<Object> hashValueSerializer = hashValueSerializerOf();
        byte[] payload = "[\"javax.naming.InitialContext\",{}]".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> hashValueSerializer.deserialize(payload),
                "哈希值序列化不得绕过白名单");
    }

    @Test
    @DisplayName("缓存管理器可装配，且按名取用缓存可用")
    void cacheManagerIsCreated() {
        CacheManager cacheManager = configuration.cacheManager(mock(RedisConnectionFactory.class));
        assertTrue(cacheManager.getCacheNames().isEmpty(),
                "未预置缓存名时集合应为空（按需创建），实际：" + cacheManager.getCacheNames());
        assertNotNull(cacheManager.getCache("jffast:demo"), "按名取缓存必须拿到实例");
    }

    @Test
    @DisplayName("wiselyKeyGenerator 拼出 类名+方法名+入参，且 null 入参不得抛 NPE")
    void keyGeneratorIsStableAndNullSafe() throws Exception {
        KeyGenerator keyGenerator = configuration.wiselyKeyGenerator();
        Method method = String.class.getMethod("length");
        Object target = "sample";

        String key = String.valueOf(keyGenerator.generate(target, method, new Object[]{"a", null}));

        assertEquals("java.lang.Stringlength" + "a" + "null", key,
                "缓存键必须由类名+方法名+入参拼成，实际：" + key);
    }

    private RedisSerializer<Object> valueSerializerOf() {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) wiredTemplate().getValueSerializer();
        return serializer;
    }

    private RedisSerializer<Object> hashValueSerializerOf() {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) wiredTemplate().getHashValueSerializer();
        return serializer;
    }
}
