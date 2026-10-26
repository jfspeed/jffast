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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/**
 * profile 装配契约：仓库内任何 {@code application-*.yml}（含 starter 的导入片段）都不得声明
 * {@code spring.profiles.include / active}——Spring Boot 2.4+ 对 profile 专属文档里的这两个键直接判非法
 * （InvalidConfigDataPropertyException），组内展开统一用非专属文档里的 {@code spring.profiles.group}。
 *
 * <p>本契约此前只靠人工验证：README 把 demo 档当头条能力宣传，而 {@code application-demo.yml} 里写了
 * {@code spring.profiles.include: dev}，实测 {@code --spring.profiles.active=demo} 与 {@code demo,dev}
 * 两种传法都启动失败。门禁只起 dev，所以看不见——故把它压成断言。
 *
 * <p>断言走 YAML 结构而非文本正则：{@code management.endpoints.web.exposure.include} 也是 {@code include:}，
 * 正则门禁会把合法配置判成违规。
 *
 * @author jffast
 */
class ProfileActivationContractTest {

    /** 一份 classpath 上的 application-*.yml 及其解析出的多文档。 */
    private record ProfileDocument(String fileName, List<Map<String, Object>> documents) {
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parse(String text) {
        return StreamSupport.stream(new Yaml().loadAll(text).spliterator(), false)
                .filter(Objects::nonNull)
                .map(doc -> (Map<String, Object>) doc)
                .toList();
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = ProfileActivationContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "缺少资源：" + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Map<String, Object>> base() throws IOException {
        return parse(readResource("/application.yml"));
    }

    /**
     * 本仓自身的 {@code application-*.yml}（多模块 classpath 合并后的结果）。
     * 只取 file: 协议——第三方 jar 里的同名文件不归本项目管，混进来只会造成假阳性。
     */
    private static List<ProfileDocument> profileDocuments() throws IOException {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath*:application-*.yml");
        List<ProfileDocument> result = new ArrayList<>();
        for (Resource resource : found) {
            if (!"file".equals(resource.getURL().getProtocol())) {
                continue;
            }
            String text;
            try (InputStream in = resource.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            result.add(new ProfileDocument(String.valueOf(resource.getFilename()), parse(text)));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object value = current.get(key);
            if (!(value instanceof Map)) {
                return Map.of();
            }
            current = (Map<String, Object>) value;
        }
        return current;
    }

    private static List<String> expanded(Object targets) {
        if (targets instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return targets == null ? List.of() : List.of(String.valueOf(targets));
    }

    @Test
    @DisplayName("application-*.yml 不得声明 spring.profiles.include / active")
    void noForbiddenActivationKeys() throws IOException {
        List<ProfileDocument> documents = profileDocuments();
        assertFalse(documents.isEmpty(), "classpath 上没扫到任何 application-*.yml，本断言形同虚设");
        for (ProfileDocument document : documents) {
            for (Map<String, Object> doc : document.documents()) {
                Map<String, Object> profiles = nested(doc, "spring", "profiles");
                assertTrue(!profiles.containsKey("include") && !profiles.containsKey("active"),
                        document.fileName() + " 写了 spring.profiles.include/active —— Spring Boot 会判非法并抛 "
                                + "InvalidConfigDataPropertyException（无论是否再显式传该 profile 都起不来），"
                                + "组内展开请改到 application.yml 的 spring.profiles.group");
            }
        }
    }

    @Test
    @DisplayName("spring.profiles.group 的每个展开目标都有对应配置文件")
    void groupTargetsResolve() throws IOException {
        List<String> fileNames = profileDocuments().stream().map(ProfileDocument::fileName).toList();
        Map<String, Object> group = nested(base().get(0), "spring", "profiles", "group");
        assertFalse(group.isEmpty(), "application.yml 必须声明 spring.profiles.group（跨 profile 组合的唯一入口）");
        group.forEach((profile, targets) -> {
            List<String> expand = expanded(targets);
            assertFalse(expand.isEmpty(), "profile 组 " + profile + " 展开目标为空");
            for (String target : expand) {
                assertTrue(fileNames.contains("application-" + target + ".yml"),
                        "profile 组 " + profile + " 引用了 " + target + "，但 classpath 上没有 application-"
                                + target + ".yml —— 组内展开会静默失效");
            }
        });
    }

    @Test
    @DisplayName("demo 档：靠组内展开拿到 dev 基础配置，并自己打开 demo-mode")
    void demoProfileIsWired() throws IOException {
        Map<String, Object> group = nested(base().get(0), "spring", "profiles", "group");
        assertTrue(expanded(group.get("demo")).contains("dev"),
                "README 宣传的 `--spring.profiles.active=demo` 必须有 spring.profiles.group.demo=[dev] 才成立");

        List<Map<String, Object>> demo = parse(readResource("/application-demo.yml"));
        boolean demoModeEnabled = demo.stream().anyMatch(doc -> {
            Map<String, Object> demoMode = nested(doc, "jffast", "demo-mode");
            return Boolean.TRUE.equals(demoMode.get("enabled"));
        });
        assertTrue(demoModeEnabled, "application-demo.yml 必须显式打开 jffast.demo-mode.enabled，否则 demo 档只剩名字");
    }

    @Test
    @DisplayName("未指定 profile 时默认 dev，新人 clone 后零配置可跑")
    void defaultProfileIsDev() throws IOException {
        Map<String, Object> profiles = nested(base().get(0), "spring", "profiles");
        assertEquals("dev", String.valueOf(profiles.get("default")),
                "spring.profiles.default 必须是 dev（Docker / 生产部署由 SPRING_PROFILES_ACTIVE 显式覆盖）");
    }
}
