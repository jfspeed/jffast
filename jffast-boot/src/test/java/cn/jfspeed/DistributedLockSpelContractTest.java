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

import cn.jfspeed.security.annotation.DistributedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SpEL 参数名契约门禁（纯反射，无需 MySQL / Redis）。
 *
 * <p>为什么存在：{@code @DistributedLock(key = "'role:' + #req.id")} 里的 {@code #req} 是
 * <b>字符串契约</b>——不受 javac 检查。把 Controller 方法参数从 {@code req} 改名（或生成器
 * 产出与 DTO 不匹配的表达式）后：{@code DistributedLockAspect#parseKey} 的 SpEL 上下文里
 * 没有该变量，轻则求值为 {@code null}（锁退化成同一把 key，形同失效），重则属性不存在直接抛
 * {@code SpelEvaluationException} → 接口 500。两种都只在运行到该端点时才暴露。
 *
 * <p>本测试用与切面<b>相同</b>的 {@link DefaultParameterNameDiscoverer} 解析真实参数名，
 * 断言每个 {@code #x} 都是该方法的真实参数名，把「运行期才发现」左移到「构建即发现」。
 *
 * <p>扫描方式与 {@link EndpointPermissionAnnotationTest} 一致：{@code Class.forName(name,false)}
 * + {@code getDeclaredMethods()}，避免加载 provided-scope 依赖（如腾讯 COS SDK）导致
 * {@code NoClassDefFoundError}。
 *
 * <p>范围说明：{@code @Idempotent} 没有 SpEL 属性（key = userId + URI + 参数 hash），
 * 故只校验 {@link DistributedLock}。新增带 SpEL 的注解时应在本测试内一并纳入。
 *
 * @author jffast
 */
class DistributedLockSpelContractTest {

    /** SpEL 变量引用：# 后跟标识符（如 #{#id} 模板与 #req.id 表达式都能命中）。 */
    private static final Pattern SPEL_VARIABLE = Pattern.compile("#([A-Za-z_$][A-Za-z0-9_$]*)");

    /** SpEL 内建变量，不是方法参数。 */
    private static final Set<String> RESERVED = Set.of("this", "root");

    @Test
    @DisplayName("@DistributedLock 的 key 中 #参数名 必须是方法真实参数名")
    void distributedLockSpelParamsMustBeRealParameterNames() throws Exception {
        ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
        List<String> violations = new ArrayList<>();
        int checked = 0;

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        // 全量扫描（@DistributedLock 打在方法上，无法用类级 AnnotationTypeFilter 过滤）
        scanner.addIncludeFilter((metadataReader, factory) -> true);

        for (BeanDefinition bd : scanner.findCandidateComponents("cn.jfspeed")) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(className, false,
                        Thread.currentThread().getContextClassLoader());
                for (Method method : clazz.getDeclaredMethods()) {
                    DistributedLock lock = method.getAnnotation(DistributedLock.class);
                    if (lock == null) {
                        continue;
                    }
                    String key = lock.key();
                    if (key == null || !key.contains("#")) {
                        continue;
                    }
                    checked++;

                    String[] names = discoverer.getParameterNames(method);
                    Set<String> params = names == null ? Set.of() : new HashSet<>(Arrays.asList(names));
                    for (String variable : extractVariables(key)) {
                        if (RESERVED.contains(variable) || params.contains(variable)) {
                            continue;
                        }
                        violations.add(clazz.getSimpleName() + "#" + method.getName()
                                + " 的 key=\"" + key + "\" 引用了 #" + variable
                                + "，但该方法的真实参数名是 "
                                + (names == null ? "<不可解析，请检查 -parameters/调试信息>" : Arrays.toString(names)));
                    }
                }
            } catch (Throwable ignored) {
                // 类引用了不在 test classpath 的 optional 依赖（如腾讯 COS SDK）时反射会抛
                // NoClassDefFoundError。@DistributedLock 只出现在 Controller（依赖可解析），
                // 跳过这类类不影响覆盖；末尾 checked >= 10 兜底防「扫描静默失效」。
            }
        }

        // 防退化：扫描失效时不能静默通过（实际加锁端点已知 >= 12 个）
        assertTrue(checked >= 10,
                "只扫描到 " + checked + " 个含 SpEL 的 @DistributedLock 端点，疑似扫描/类路径异常");
        assertTrue(violations.isEmpty(),
                "SpEL 参数名契约违规（改参数名后运行期会静默 key=null 或抛 SpelEvaluationException）：\n  - "
                        + String.join("\n  - ", violations));
    }

    private static List<String> extractVariables(String spel) {
        List<String> variables = new ArrayList<>();
        Matcher matcher = SPEL_VARIABLE.matcher(spel);
        while (matcher.find()) {
            variables.add(matcher.group(1));
        }
        return variables;
    }
}