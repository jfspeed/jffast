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
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Flowable 引擎配置回归测试（无需 MySQL / Redis，可在任意环境运行）。
 *
 * <p>背景：Flowable 8 的 process starter 默认把 event-registry 引擎也带入
 * （7.x 没有），且经 flowable-spring-security 传递带入 IDM 引擎
 * （IdentityService 被 FlowService 注入）。各引擎启动时都会做 schema 建表/升级，
 * 种子 DDL 的版本行若与引擎版本不一致，就会走升级路径并在并发提交版本行时抛
 * {@code FlowableOptimisticLockingException}，启动即失败。
 * 2026-10-13 冷启动实证：7.1.0.2 种子 + Flowable 8.0.0 引擎必现（process 与 IDM 都会升级），
 * 关 event-registry 只能解决其中一路。
 *
 * <p>三条断言各锁一个真实踩过的坑：
 * <ul>
 *   <li>键名必须是 Flowable 8 的精确前缀 {@code flowable.eventregistry}
 *      （门控 {@code ConditionalOnEventRegistry} 只认它）；</li>
 *   <li>不得误配到 {@code jffast.flowable} 下（根键错误，引擎不读取，等于没关）；</li>
 *   <li>种子 DDL 的版本行必须等于 BOM 的 Flowable 版本（升级 Flowable 只改 BOM
 *      却不刷种子 = 下一次全新建库必起不来）。</li>
 * </ul>
 *
 * @author jffast
 */
class FlowableConfigTest {

    @Test
    void eventRegistryMustBeDisabledWithExactKey() {
        Map<String, Object> yml = loadAppYml();

        Object flowable = yml.get("flowable");
        if (!(flowable instanceof Map)) {
            fail("application.yml 缺少根 flowable 配置段");
        }
        Map<?, ?> flowableMap = (Map<?, ?>) flowable;
        Object eventregistry = flowableMap.get("eventregistry");
        if (!(eventregistry instanceof Map)) {
            fail("application.yml 缺少 flowable.eventregistry 配置段（Flowable 8 精确前缀，无短横线）");
        }
        Map<?, ?> registryMap = (Map<?, ?>) eventregistry;
        assertEquals(Boolean.FALSE, registryMap.get("enabled"),
                "flowable.eventregistry.enabled 必须为 false，否则与 process 引擎抢 schema.version 行导致启动失败");
    }

    /**
     * BPMN 部署 / 解析必须走安全 XML（禁 DOCTYPE 与外部实体）。
     * <p>
     * 本仓开放 {@code POST /flow/process/deploy} 接收任意 BPMN XML，而 flowable-engine ≤ 8.0.0
     * 的 XML 解析链路存在 XXE 类缺陷（CVE-2026-91197，截至 2026-10 官方仍无修复版本），上游加固依赖
     * {@code enableSafeBpmnXml}。
     * <p>
     * Spring Boot 场景下 {@code FlowableProcessProperties#enableSafeXml} 默认即为 true，此处显式配置
     * 作为「明示意图 + 防上游改默认值」的双保险。本测试守护两件事：<ol>
     *   <li>键名必须是 {@code enable-safe-xml}（源自字段 enableSafeXml）；</li>
     *   <li>不能误写成 {@code enable-safe-bpmn-xml} —— 那是无效键，宽松绑定静默忽略，等于没配。</li>
     * </ol>
     */
    @Test
    void bpmnXmlParsingMustBeEnabledWithExactSafeKey() {
        Map<String, Object> yml = loadAppYml();

        Object flowable = yml.get("flowable");
        if (!(flowable instanceof Map)) {
            fail("application.yml 缺少根 flowable 配置段");
        }
        Map<?, ?> flowableMap = (Map<?, ?>) flowable;
        Object process = flowableMap.get("process");
        if (!(process instanceof Map)) {
            fail("application.yml 缺少 flowable.process 配置段");
        }
        Map<?, ?> processMap = (Map<?, ?>) process;
        assertFalse(processMap.containsKey("enable-safe-bpmn-xml"),
                "flowable.process.enable-safe-bpmn-xml 是无效键（真实字段名是 enableSafeXml），"
                        + "Spring Boot 会静默忽略它 → BPMN 解析仍可能受 XXE 影响；请改为 enable-safe-xml");
        assertEquals(Boolean.TRUE, processMap.get("enable-safe-xml"),
                "必须显式开启 flowable.process.enable-safe-xml，否则 BPMN 部署/解析链路可被 XXE 利用"
                        + "（/flow/process/deploy 对外接收任意 BPMN XML）");
    }

    @Test
    void eventRegistryMustNotHideUnderJffastKey() {
        Map<String, Object> yml = loadAppYml();

        Object jffast = yml.get("jffast");
        if (jffast instanceof Map<?, ?> jffastMap) {
            assertFalse(jffastMap.containsKey("flowable"),
                    "jffast.flowable 下的 event-registry 开关引擎不读取（根键错误），必须放在根 flowable 段");
        }
    }

    @Test
    void seedSchemaVersionsMustMatchBomFlowableVersion() throws Exception {
        // 种子版本 vs BOM 的 Flowable 版本：两者必须逐字相等。
        // 否则全新库按 README 建库后，process / IDM 引擎启动即走升级路径，
        // 并在提交版本行时报 FlowableOptimisticLockingException（2026-10-13 冷启动实证）。
        String expected = readBomFlowableVersion();
        java.util.List<String> seedFiles = java.util.List.of(
                "flowable_mysql_all.sql",
                "flowable_common.sql",
                "flowable_engine.sql",
                "flowable_history.sql",
                "flowable_eventregistry.sql");
        java.nio.file.Path schemaDir = repoRoot()
                .resolve("sql").resolve("flowable_schema");
        java.util.regex.Pattern pin = java.util.regex.Pattern.compile(
                "(?i)(?:insert\\s+into|replace\\s+into)\\s+`?(?:ACT_GE_PROPERTY|ACT_ID_PROPERTY)`?\\s*"
                        + "(?:\\([^)]*\\)\\s*)?values\\s*\\(\\s*'([^']+\\.schema\\.version|schema\\.version|schema\\.history)'\\s*,\\s*'([^']+)'");
        boolean checkedAny = false;
        for (String name : seedFiles) {
            java.nio.file.Path file = schemaDir.resolve(name);
            if (!java.nio.file.Files.isRegularFile(file)) {
                fail("种子文件缺失：" + file + "（聚合与拆分文件必须成套存在）");
            }
            String sql = java.nio.file.Files.readString(file);
            java.util.regex.Matcher m = pin.matcher(sql);
            while (m.find()) {
                checkedAny = true;
                String seedVersion = m.group(2);
                String seedCore = seedVersion.startsWith("create(") && seedVersion.endsWith(")")
                        ? seedVersion.substring("create(".length(), seedVersion.length() - 1)
                        : seedVersion;
                // artifact 版本（8.0.0）与 schema 版本（8.0.0.0）不是同一编号体系：
                // 断言种子版本以 BOM 版本为前缀（8.0.0.x），跨大版本漂移（如 7.1.0.2 vs 8.0.0）仍会失败。
                assertTrue(seedCore.equals(expected) || seedCore.startsWith(expected + "."),
                        "种子版本行 " + m.group(1) + " 在 " + name + " 中为 "
                                + seedVersion + "，与 BOM 的 Flowable " + expected + " 不是同一版本线");
            }
        }
        if (!checkedAny) {
            fail("种子文件中一个版本行都没匹配到，断言本身可能已失效（正则或种子格式漂移）");
        }
    }

    private static String readBomFlowableVersion() throws Exception {
        java.nio.file.Path bom = repoRoot().resolve("jffast-bom").resolve("pom.xml");
        if (!java.nio.file.Files.isRegularFile(bom)) {
            fail("找不到 BOM：" + bom + "（测试必须在仓库布局下运行）");
        }
        String pom = java.nio.file.Files.readString(bom);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "flowable-spring-boot-starter-process</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(pom);
        if (!m.find()) {
            fail("BOM 中找不到 flowable-spring-boot-starter-process 的 version");
        }
        return m.group(1).trim();
    }

    private static java.nio.file.Path repoRoot() {
        // surefire 的工作目录一般是模块目录（jffast-boot），但不硬编码层级：
        // 从 cwd 逐级向上找同时含 sql/ 与 jffast-bom/ 的目录。
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("sql"))
                    && java.nio.file.Files.isDirectory(dir.resolve("jffast-bom"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        fail("无法定位仓库根（从 " + java.nio.file.Path.of("").toAbsolutePath()
                + " 向上找不到同时含 sql/ 与 jffast-bom/ 的目录）");
        throw new IllegalStateException("unreachable");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadAppYml() {
        try (InputStream in = FlowableConfigTest.class.getResourceAsStream("/application.yml")) {
            if (in == null) {
                fail("classpath 下找不到 application.yml");
            }
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("解析 application.yml 失败", e);
        }
    }
}
