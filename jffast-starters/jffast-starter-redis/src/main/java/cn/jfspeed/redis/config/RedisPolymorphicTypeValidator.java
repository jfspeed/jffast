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

import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.util.Set;

/**
 * Redis 值反序列化的**多态类型白名单**。
 *
 * <p><b>为什么必须有它</b>：{@code RedisTemplate} 的值序列化目标是 {@code Object.class}，
 * 且开启 {@code DefaultTyping.NON_FINAL} 以便把 {@code List<SysDict>} 这类集合原样读回。
 * 但 Jackson 的多态反序列化在「类型白名单放行全部」时，就等价于
 * <b>DefaultTyping RCE</b>：任何能写入本应用 Redis 键的一方（未授权暴露的 Redis、
 * SSRF、被污染的共享实例、持有 Redis 凭据的组件）都可以投递一个指定类型的载荷，
 * 由应用反序列化并触发其副作用。
 *
 * <p>本仓对 <b>Sa-Token 会话</b>的同类风险已经用 {@code sa-json-allow-types} 收敛
 * （见 {@code application.yml}）——Redis 侧此前却是「复刻 LaissezFaire 语义放行全部」，
 * 与本仓自身的安全声明矛盾。此处改为与该白名单同口径的显式白名单。
 *
 * <p><b>白名单范围</b>（Base 实际写入 Redis 的全部类型）：
 * <ul>
 *   <li>{@code cn.jfspeed.*} —— 本项目自有类型（如 {@code cn.jfspeed.system.entity.SysDict}）</li>
 *   <li>{@code java.lang} 的包装类型与字符串</li>
 *   <li>{@code java.util} 的常用集合实现（作为容器）</li>
 * </ul>
 * 其余一律拒绝 —— 第三方库（commons-collections / spring / xstream 等）的反序列化
 * gadget 一律落在白名单之外。
 *
 * <p><b>失败关闭</b>：命中白名单外的类型名时 Jackson 抛
 * {@code InvalidTypeIdException}，请求失败，<b>不会</b>退化成「当作 Object 继续」。
 *
 * <p><b>缓存值的写法约束</b>（与本白名单无关，是 {@code DefaultTyping} 的既有限制）：
 * {@code List.of(..)} / {@code Set.of(..)} / {@code Map.of(..)} / {@code stream().toList()}
 * 这类 JDK <em>不可变</em>集合在 {@code NON_FINAL} 默认类型下<b>不会</b>写出类型标记，
 * 因此以 {@code Object} 读回时必然失败——换成放行全部的校验器也一样失败（已实测）。
 * 需要进 Redis 的集合请写成 {@code new ArrayList<>(..)} / {@code new HashMap<>(..)}；
 * {@code Arrays.asList(..)}、{@code Collections.unmodifiableList(..)} 等<b>只读壳</b>带类型标记，已列入白名单。
 *
 * @author jffast
 */
public final class RedisPolymorphicTypeValidator extends PolymorphicTypeValidator {

    /** 供序列化器使用的单例（无状态，线程安全）。 */
    public static final RedisPolymorphicTypeValidator INSTANCE = new RedisPolymorphicTypeValidator();

    /** 本项目自有类型的包前缀（项目内新增实体无需改白名单）。 */
    private static final String PROJECT_PACKAGE_PREFIX = "cn.jfspeed.";

    /** Base 实际写入 Redis 的 JDK 类型白名单（含集合容器实现）。 */
    private static final Set<String> ALLOWED_TYPE_NAMES = Set.of(
            // 基本包装类型与字符串：验证码、JSON 串、锁 token、计数
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Boolean",
            "java.lang.Double",
            "java.lang.Float",
            "java.lang.Short",
            "java.lang.Byte",
            "java.lang.Character",
            "java.lang.Number",
            // 集合容器：权限列表、字典缓存
            "java.util.ArrayList",
            "java.util.LinkedList",
            "java.util.Vector",
            "java.util.Stack",
            "java.util.HashSet",
            "java.util.LinkedHashSet",
            "java.util.TreeSet",
            "java.util.HashMap",
            "java.util.LinkedHashMap",
            "java.util.TreeMap",
            "java.util.Hashtable",
            "java.util.Properties",
            // JDK 提供的只读/定长**容器壳**（元素类型仍逐个过本校验器）
            "java.util.Arrays$ArrayList",
            "java.util.Collections$UnmodifiableList",
            "java.util.Collections$UnmodifiableSet",
            "java.util.Collections$UnmodifiableMap",
            "java.util.Collections$UnmodifiableRandomAccessList",
            "java.util.Collections$SingletonList",
            "java.util.Collections$SingletonSet",
            "java.util.Collections$SingletonMap",
            "java.util.Collections$EmptyList",
            "java.util.Collections$EmptySet",
            "java.util.Collections$EmptyMap");

    /**
     * 判断一个类名是否在白名单内。
     *
     * @param className 全限定类名
     * @return 是否允许反序列化为该类型
     */
    public static boolean isAllowedTypeName(String className) {
        if (className == null || className.isEmpty()) {
            return false;
        }
        return className.startsWith(PROJECT_PACKAGE_PREFIX)
                || ALLOWED_TYPE_NAMES.contains(className);
    }

    @Override
    public Validity validateBaseType(DatabindContext ctxt, JavaType baseType) {
        // 绝不能在这里返回 ALLOWED：baseType 是 Object.class，一旦放行就会跳过子类型校验，
        // 白名单形同虚设。INDETERMINATE = 交给下面的子类型校验逐个判。
        return Validity.INDETERMINATE;
    }

    @Override
    public Validity validateSubClassName(DatabindContext ctxt, JavaType baseType, String subClassName) {
        return isAllowedTypeName(subClassName) ? Validity.ALLOWED : Validity.DENIED;
    }

    @Override
    public Validity validateSubType(DatabindContext ctxt, JavaType baseType, JavaType subType) {
        // Jackson 3 的 JavaType 没有 getName()，取全限定名要走 raw class。
        String className = subType == null ? null : subType.getRawClass().getName();
        return isAllowedTypeName(className) ? Validity.ALLOWED : Validity.DENIED;
    }
}
