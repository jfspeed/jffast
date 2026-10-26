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
package cn.jfspeed;

import cn.dev33.satoken.json.SaJsonTemplateForJackson3;
import cn.dev33.satoken.strategy.SaJsonStrategy;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysRole;
import cn.jfspeed.system.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Sa-Token session 白名单回归测试（无需 MySQL / Redis，可在任意环境运行）。
 *
 * <p>背景：Sa-Token 1.46 起 JSON 多态反序列化受全局类型白名单约束
 * （{@code SaJsonStrategy}，修复 DefaultTyping RCE）。登录时
 * {@code LoginUtil.login(user)} 把 {@link SysUser} 整体写入 session，
 * 其嵌套的每一个自定义类型都必须出现在
 * {@code jffast.security.sa-json-allow-types} 中，否则读 session 时反序列化被拒，
 * 表现为「登录成功但取不到用户」（异常被 {@code LoginUtil} 吞掉，静默降级为未登录）。
 *
 * <p>两条用例分工：
 * <ul>
 *   <li>{@link #whitelistMustCoverSessionGraph} —— 配置与代码的一致性契约：
 *       从 {@code SysUser} 出发反射收集 session 可达的全部自有类型，
 *       断言都被 {@code application.yml} 的白名单覆盖。实体加字段后漏配会直接红。</li>
 *   <li>{@link #sessionUserMustRoundTripThroughSaTokenJson} —— 真实行为复现：
 *       用 yml 白名单原样注册后走 Sa-Token 自己的序列化模板做往返，
 *       覆盖「配了但配不全」导致的 denied-resolution。</li>
 * </ul>
 *
 * @author jffast
 */
class SaJsonSessionWhitelistTest {

    /** session 可达类型搜索深度：SysUser → SysDept → children 足够两层。 */
    private static final int MAX_DEPTH = 4;

    @Test
    void whitelistMustCoverSessionGraph() {
        Set<String> allowed = loadWhitelistFromAppYml();
        Set<Class<?>> reachable = sessionReachableCustomTypes(SysUser.class);

        List<String> missing = new ArrayList<>();
        for (Class<?> type : reachable) {
            if (!allowed.contains(type.getName())) {
                missing.add(type.getName());
            }
        }
        assertTrue(missing.isEmpty(),
                "sa-json-allow-types 漏配 session 可达类型，登录后读 session 会被拒（静默未登录）: " + missing);
    }

    @Test
    void sessionUserMustRoundTripThroughSaTokenJson() {
        Set<String> allowed = loadWhitelistFromAppYml();

        SaJsonStrategy.instance.resetState();
        for (String typeName : allowed) {
            try {
                SaJsonStrategy.instance.registerAllowType(Class.forName(typeName));
            } catch (ClassNotFoundException e) {
                fail("sa-json-allow-types 配置的类不存在: " + typeName);
            }
        }
        SaJsonTemplateForJackson3 json = new SaJsonTemplateForJackson3();

        SysUser user = sessionUserFixture();
        String text = json.objectToJson(user);
        SysUser back = json.jsonToObject(text, SysUser.class);

        assertEquals("admin", back.getUserName());
        assertEquals(1, back.getRoleList().size());
        assertEquals("super_admin", back.getRoleList().get(0).getRoleKey());
        assertEquals(1, back.getDeptList().size());
        assertEquals("研发部", back.getDeptList().get(0).getDeptName());
        assertEquals("研发一组", back.getDeptList().get(0).getChildren().get(0).getDeptName());
        assertEquals("10", back.getDataScope().get("deptId"));
    }

    /**
     * 按登录写入时的真实形态组装：roleList / deptList（含 children）/ dataScope 全量填充。
     * 缺任何一项都复现不出白名单漏配（null 字段不写类型信息，反序列化不会被拒）。
     */
    private static SysUser sessionUserFixture() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUserName("admin");
        user.setUpdatePwdTime(LocalDateTime.of(2026, 10, 13, 12, 0, 0));

        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleKey("super_admin");
        role.setRoleName("超级管理员");
        user.setRoleList(List.of(role));

        SysDept child = new SysDept();
        child.setId(11L);
        child.setDeptName("研发一组");

        SysDept dept = new SysDept();
        dept.setId(10L);
        dept.setDeptName("研发部");
        dept.setChildren(List.of(child));
        user.setDeptList(List.of(dept));

        Map<String, Object> dataScope = new HashMap<>();
        dataScope.put("deptId", "10");
        dataScope.put("deptName", "研发部");
        user.setDataScope(dataScope);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> loadWhitelistFromAppYml() {
        try (InputStream in = SaJsonSessionWhitelistTest.class.getResourceAsStream("/application.yml")) {
            if (in == null) {
                fail("classpath 下找不到 application.yml");
            }
            Map<String, Object> yml = new Yaml().load(in);
            Map<String, Object> jffast = (Map<String, Object>) yml.get("jffast");
            Map<String, Object> security = (Map<String, Object>) jffast.get("security");
            List<String> types = (List<String>) security.get("sa-json-allow-types");
            if (types == null) {
                fail("application.yml 缺少 jffast.security.sa-json-allow-types");
            }
            return new HashSet<>(types);
        } catch (Exception e) {
            throw new IllegalStateException("解析 application.yml 白名单失败", e);
        }
    }

    /**
     * 从根类型出发收集字段可达的全部自有（cn.jfspeed）类型。
     * JDK 类型（String/Long/Map/List/java.time 等）由 Sa-Token 内置放行，无需注册。
     */
    private static Set<Class<?>> sessionReachableCustomTypes(Class<?> root) {
        Set<Class<?>> found = new HashSet<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Map.Entry<Class<?>, Integer>> queue = new ArrayDeque<>();
        queue.add(Map.entry(root, 0));
        visited.add(root);

        while (!queue.isEmpty()) {
            Map.Entry<Class<?>, Integer> current = queue.poll();
            Class<?> clazz = current.getKey();
            int depth = current.getValue();
            if (depth >= MAX_DEPTH) {
                continue;
            }
            for (Class<?> fieldType : fieldTypesOf(clazz)) {
                if (isJdkType(fieldType)) {
                    continue;
                }
                if (fieldType.getName().startsWith("cn.jfspeed")) {
                    found.add(fieldType);
                }
                if (visited.add(fieldType)) {
                    queue.add(Map.entry(fieldType, depth + 1));
                }
            }
        }
        return found;
    }

    private static Set<Class<?>> fieldTypesOf(Class<?> clazz) {
        Set<Class<?>> types = new HashSet<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                collectType(field.getGenericType(), types);
            }
        }
        return types;
    }

    private static void collectType(Type type, Set<Class<?>> out) {
        if (type instanceof Class<?> c) {
            if (c.isArray()) {
                collectType(c.getComponentType(), out);
            } else {
                out.add(c);
            }
        } else if (type instanceof ParameterizedType p) {
            collectType(p.getRawType(), out);
            for (Type arg : p.getActualTypeArguments()) {
                collectType(arg, out);
            }
        }
    }

    private static boolean isJdkType(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return true;
        }
        String name = clazz.getName();
        return name.startsWith("java.")
                || name.startsWith("javax.")
                || name.startsWith("jakarta.")
                || name.startsWith("jdk.");
    }
}
