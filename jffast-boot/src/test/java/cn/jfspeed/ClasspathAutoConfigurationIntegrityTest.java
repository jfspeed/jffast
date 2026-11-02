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

import org.junit.jupiter.api.Test;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 类路径完整性测试：自动配置类所引用的类型必须在类路径上（无需 MySQL / Redis）。
 *
 * <p><b>为什么存在（2026-11-02 事故）</b><br>
 * 那天后端起不来，日志是：
 * <pre>
 * NoClassDefFoundError: com/baomidou/mybatisplus/spring/MybatisSqlSessionFactoryBean
 *   at java.lang.Class.getDeclaredMethods0(Native Method)
 *   ...
 *   Error processing condition on MybatisPlusAutoConfiguration.sqlSessionFactory
 * </pre>
 * 根因是 BOM 把 MyBatis-Plus 这一族 6 个坐标里的 3 个升到 3.5.18.1、另 3 个留在 3.5.16。
 * Maven 的 dependencyManagement <b>静默</b>覆盖传递依赖版本，于是构建期无告警、
 * 单测全绿、CI 全绿；而 3.5.17 把 {@code MybatisSqlSessionFactoryBean} 从
 * {@code extension.spring} 挪到了 {@code spring}——自动配置类是按新包名编译的，
 * 类路径上却只有旧包名的 jar。只有真正刷新 Spring 上下文时才会炸。
 *
 * <p><b>本测试把这条故障左移到构建期</b>：{@code Class.getDeclaredMethods()} 会解析
 * 方法签名里的每个类型，缺类立刻抛 {@link NoClassDefFoundError}。于是「混版本导致的
 * 缺类」在 {@code mvn test} 阶段就会失败，而不是等到起服务。
 *
 * <p><b>判定条件必须与 Spring 一致，否则全是误报</b>。Spring 读自动配置类的注解
 * 走的是 <b>ASM</b>（{@code SimpleMetadataReader}），不加载类；只有当它真的要为一个
 * 标了条件注解的 {@code @Bean} 方法做「类型推导」时，才回头用反射去读那个配置类的
 * 方法签名——那一步才会解析类型、才会炸。
 *
 * <p>所以本测试复刻 Spring 的判定顺序：
 * <ol>
 *   <li>用 ASM 读类上的 {@code @ConditionalOnClass}（类名按字符串读，不加载）；
 *       不满足就跳过——Spring 整类跳过，根本不会反射它。</li>
 *   <li>用 ASM 列出带条件注解的方法；一个都没有就跳过——Spring 不会反射它。</li>
 *   <li>只有上面两步都过了，才反射这个类并断言方法签名引用的类型都在类路径上。</li>
 * </ol>
 * 少了第 1 步，Micrometer / springdoc / sa-token / Flowable 那些「可选依赖缺席」的
 * 自动配置类会全部误报（它们的 {@code @ConditionalOnClass} 里写的正是不在类路径上的类）。
 *
 * <p>断言用的类名 / 方法签名全部来自被测类的字节码，<b>不硬编码任何包名</b>：
 * 上游再挪一次包名，这个测试会跟着读新的名字继续校验，而不是无脑报红。
 *
 * @author jffast
 */
class ClasspathAutoConfigurationIntegrityTest {

    /** Spring Boot 4 仍然用这个清单文件名登记自动配置类。 */
    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final String CONDITIONAL_ON_CLASS =
            "org.springframework.boot.autoconfigure.condition.ConditionalOnClass";

    /**
     * 类级条件注解：Spring 先求值它们，全过才登记这个配置类。
     *
     * <p>任何一个条件引用了类路径上不存在的类型，条件就不可能成立 → 整类跳过。
     */
    private static final List<String> CLASS_LEVEL_CONDITIONS = List.of(
            CONDITIONAL_ON_CLASS,
            "org.springframework.boot.autoconfigure.condition.ConditionalOnBean",
            "org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean",
            "org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate");

    /**
     * 会触发「推导 bean 类型」的条件注解：{@code OnBeanCondition} 这一族。
     *
     * <p>只有它们在求值时需要反射配置类的方法签名——也就是只有它们能让「类路径上缺一个
     * 被方法签名引用的类」变成启动期故障。这里存的是<b>简单名</b>：字节扫描按常量池里的
     * 注解描述符匹配，描述符里带的是简单名。
     */
    private static final List<String> ON_BEAN_CONDITIONS = List.of(
            "ConditionalOnMissingBean",
            "ConditionalOnBean",
            "ConditionalOnSingleCandidate");

    private static final String MYBATIS_PLUS_AUTO_CONFIGURATION =
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration";

    /** ASM 读取器：与 Spring 读自动配置类用的是同一套机制。 */
    private static final MetadataReaderFactory METADATA_READERS = new SimpleMetadataReaderFactory();

    /**
     * 直接回归：出事那天就是这条路径炸的。
     *
     * <p>不断言「方法存在」——上游随时可能增删方法；只断言
     * {@code getDeclaredMethods()} 能跑完，即所有被引用的类型都在类路径上。
     */
    @Test
    void mybatisPlusAutoConfigurationIsIntrospectable() {
        Class<?> autoConfiguration = loadClass(MYBATIS_PLUS_AUTO_CONFIGURATION);
        assertNotNull(autoConfiguration, MYBATIS_PLUS_AUTO_CONFIGURATION + " 必须出现在类路径上");

        assertDoesNotThrow(
                autoConfiguration::getDeclaredMethods,
                MYBATIS_PLUS_AUTO_CONFIGURATION + " 的方法签名引用了类路径上不存在的类型。"
                        + "典型成因：依赖族只升了一半版本（构建期静默、运行期 NoClassDefFoundError）");
    }

    /**
     * 全量扫描：每一个 Spring 会真的去反射的自动配置类，其方法签名引用的类型都必须在类路径上。
     */
    @Test
    void everyAutoConfigurationOnTheClasspathIsIntrospectable() throws Exception {
        Set<String> classNames = autoConfigurationClassNames();
        assertTrue(!classNames.isEmpty(),
                "自动配置清单是空的——扫描没有真正跑起来，等于没测");

        List<String> failures = new ArrayList<>();
        int reflected = 0;
        int skipped = 0;

        for (String className : classNames) {
            String binaryName = resolveBinaryName(className);
            if (binaryName == null) {
                // 清单里写了这个类，类路径上却没有：Spring 自己也会跳过它，不算故障。
                skipped++;
                continue;
            }
            if (!classLevelConditionsCanHold(binaryName)) {
                skipped++;
                continue;
            }
            Class<?> type = loadClass(binaryName);
            if (type == null) {
                skipped++;
                continue;
            }
            if (!hasConditionalBeanMethod(type)) {
                // Spring 不带条件注解的 @Bean 方法不会触发反射式类型推导。
                skipped++;
                continue;
            }
            reflected++;
            try {
                type.getDeclaredMethods();
            } catch (NoClassDefFoundError error) {
                failures.add(className + " -> " + error.getMessage());
            }
        }

        assertTrue(reflected > 0,
                "没有任何自动配置类被实际校验（全部被跳过）——扫描等于没跑");
        assertTrue(failures.isEmpty(),
                "以下自动配置类引用了类路径上不存在的类型（多半是依赖族版本混用）：\n  "
                        + String.join("\n  ", failures)
                        + "\n提示：核对 BOM 里同一族坐标的版本是否一致"
                        + "（根 pom 的 maven-enforcer-plugin requireUpperBoundDeps 会在构建期报出来）");

        System.out.println("[ClasspathAutoConfigurationIntegrityTest] 反射校验了 " + reflected
                + " 个自动配置类，跳过 " + skipped + " 个");
    }

    /** 自动配置类名清单：类路径上所有 AutoConfiguration.imports 的并集。 */
    private Set<String> autoConfigurationClassNames() throws Exception {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<URL> resources = getClass().getClassLoader().getResources(IMPORTS_RESOURCE);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream stream = resource.openStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String name = line.trim();
                    if (name.isEmpty() || name.startsWith("#")) {
                        continue;
                    }
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * 清单里的类名 -> 二进制类名。
     *
     * <p>嵌套类在清单里用点号写（{@code A.B}），二进制名用 {@code $}
     * （{@code A$B}）——springdoc 的 imports 文件就是这么写的。
     */
    private static String resolveBinaryName(String className) {
        if (loadClass(className) != null) {
            return className;
        }
        int lastDot = className.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String candidate = className.substring(0, lastDot) + "$" + className.substring(lastDot + 1);
        return loadClass(candidate) != null ? candidate : null;
    }

    /**
     * 类级条件是否可能成立（用 ASM 读，类名按字符串读，不加载类）。
     *
     * <p>Spring 对一个配置类是「类级条件全过才登记、登记后才会反射它的方法签名」。
     * 类级条件里引用的类型只要有一个<b>不在类路径上</b>，条件就不可能成立 →
     * Spring 整类跳过 → 我们也不该去反射它。
     *
     * <p>2026-11-02 的判定就是靠这一条落地的：
     * <ul>
     *   <li>{@code MybatisPlusAutoConfiguration}：类级条件引用
     *       {@code SqlSessionFactory} / {@code org.mybatis.spring.SqlSessionFactoryBean} /
     *       {@code javax.sql.DataSource} —— 全都在类路径上 → 该查 → 反射 → 缺类暴露。</li>
     *   <li>{@code FlowableJpaAutoConfiguration}：类级 {@code @ConditionalOnBean(type=
     *       jakarta.persistence.EntityManagerFactory)}，而 JPA API 不在类路径上 → 条件不可能成立
     *       → Spring 整类跳过 → 不查（否则就是误报）。</li>
     * </ul>
     */
    private static boolean classLevelConditionsCanHold(String binaryName) {
        AnnotationMetadata metadata;
        try {
            metadata = METADATA_READERS.getMetadataReader(binaryName).getAnnotationMetadata();
        } catch (Exception error) {
            return true; // 读不到就当可以成立，宁可多查也不漏查
        }
        for (String condition : CLASS_LEVEL_CONDITIONS) {
            Map<String, Object> attributes = metadata.getAnnotationAttributes(condition, true);
            if (attributes == null) {
                continue;
            }
            for (String key : new String[]{"value", "name", "type"}) {
                for (String referenced : stringValues(attributes.get(key))) {
                    if (!isPresent(referenced)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 这个类里有没有「会让 Spring 回头反射它」的 {@code @Bean} 方法。
     *
     * <p>{@code OnBeanCondition}（也就是 {@code @ConditionalOnMissingBean} /
     * {@code @ConditionalOnBean} / {@code @ConditionalOnSingleCandidate}）在求值时要做
     * 「推导 bean 类型」，而推导靠的是<b>反射这个配置类的声明方法</b>——那就等于把该类
     * 所有方法的签名（含 private 方法的形参类型）都解析一遍。
     *
     * <p>2026-11-02 出事的就是这条路径：{@code MybatisPlusAutoConfiguration} 的
     * {@code sqlSessionFactory} 上有 {@code @ConditionalOnMissingBean}，Spring 为推导类型
     * 反射该类 → 撞上 private 方法
     * {@code applyConfiguration(com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean)}
     * 的形参类型 → 那个类不在类路径上 → {@code NoClassDefFoundError}。
     *
     * <p>没有这类注解的方法时，Spring 全程走 ASM，不会去反射这个类，缺类也就无害。
     *
     * <p>实现说明：直接扫 class 文件的常量池字节（常量池里会留下注解的类型描述符）。
     * {@code getAnnotatedMethods()} 在 Spring 7 的 ASM 元数据上返回空集，用不了；
     * 而字节扫描偏保守（宁可多查也不漏查），只会让断言更严、不会放过真问题。
     */
    private static boolean hasConditionalBeanMethod(Class<?> type) {
        String text = classFileText(type);
        for (String condition : ON_BEAN_CONDITIONS) {
            if (text.contains(condition)) {
                return true;
            }
        }
        return false;
    }

    /** class 文件的原始字节（常量池里留有注解描述符）。 */
    private static String classFileText(Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream stream = type.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        } catch (IOException error) {
            return "";
        }
    }

    /** 注解属性里的类名（ASM 读出来就是字符串，不会加载类）。 */
    private static List<String> stringValues(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof Object[]) {
            for (Object element : (Object[]) raw) {
                if (element instanceof String) {
                    values.add((String) element);
                }
            }
        } else if (raw instanceof Iterable) {
            for (Object element : (Iterable<?>) raw) {
                if (element instanceof String) {
                    values.add((String) element);
                }
            }
        }
        return values;
    }

    /** 类名在类路径上吗（不初始化，只问存在性）。 */
    private static boolean isPresent(String className) {
        if (className.isEmpty()) {
            return true;
        }
        try {
            Class.forName(className, false, ClasspathAutoConfigurationIntegrityTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError error) {
            return false;
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className, false, ClasspathAutoConfigurationIntegrityTest.class.getClassLoader());
        } catch (ClassNotFoundException | NoClassDefFoundError error) {
            return null;
        }
    }
}
