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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 出厂配置契约：任务产物目录必须落在免登录静态映射的文件存储根**之外**，且必须被 .gitignore 覆盖。
 *
 * <p>{@code SysTaskDirOutsideStorageRootTest} 已经保证「谁把 file-dir 配进存储根，启动就失败」，
 * 但那只守运行时。这里守的是仓库里那份默认配置本身——两个键分属两处
 * （{@code jffast.file.storage.local.path} 与 {@code jffast.system.task.file-dir}），改任何一个
 * 的人都不会想到另一个，而改错一次的后果是「带手机号/邮箱的用户表格 xlsx 变成 /upload/... 上的
 * 匿名下载链接」。
 *
 * <p>.gitignore 一并断言：产物目录默认落在工作目录下，没被忽略就会撞上 worktree 卫生门禁。
 *
 * @author jffast
 */
class TaskArtifactDirContractTest {

    /** {@code ${JFFAST_TASK_FILE_DIR:task-files}} -> {@code task-files}（出厂默认值）。 */
    private static String defaultValue(String raw) {
        int colon = raw.indexOf(':');
        if (!raw.startsWith("${") || colon < 0) {
            return raw;
        }
        return raw.substring(colon + 1, raw.length() - 1);
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> root, String dotted) {
        Object current = root;
        for (String segment : dotted.split("\\.")) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(segment);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> baseConfig() throws IOException {
        try (InputStream in = TaskArtifactDirContractTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(in, "缺少 /application.yml（测试类路径应含 jffast-boot 的配置）");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new Yaml().load(text);
        }
    }

    /** 从当前工作目录上溯找仓库根（含 .git 的目录）；surefire 的 cwd 是模块目录，不能直接当仓根用。 */
    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; depth < 6 && dir != null; depth++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(".git")) || Files.exists(dir.resolve(".gitignore"))) {
                return dir;
            }
        }
        throw new IllegalStateException("找不到仓库根，user.dir=" + System.getProperty("user.dir"));
    }

    @Test
    @DisplayName("默认 file-dir 与默认存储根不同目录，产物不会被 /upload/** 匿名读到")
    void defaultTaskDirIsOutsideAnonymousStorageRoot() throws IOException {
        Map<String, Object> cfg = baseConfig();
        String storageRoot = Objects.toString(dig(cfg, "jffast.file.storage.local.path"), null);
        String taskDirRaw = Objects.toString(dig(cfg, "jffast.system.task.file-dir"), null);

        assertNotNull(storageRoot, "application.yml 应显式给出 jffast.file.storage.local.path");
        assertNotNull(taskDirRaw, "application.yml 应显式给出 jffast.system.task.file-dir");

        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Path task = Paths.get(defaultValue(taskDirRaw)).toAbsolutePath().normalize();

        assertFalse(task.startsWith(root),
                "任务产物目录 " + task + " 落在免登录静态映射的存储根 " + root + " 之内");
        assertFalse(root.startsWith(task),
                "存储根 " + root + " 不应被产物目录 " + task + " 包住（两处必须互不嵌套）");
    }

    @Test
    @DisplayName("产物目录默认值已被 .gitignore 覆盖，跑过导出的工作目录不会留下未忽略文件")
    void defaultTaskDirIsGitIgnored() throws IOException {
        String taskDir = defaultValue(
                Objects.toString(dig(baseConfig(), "jffast.system.task.file-dir"), ""));
        Path gitignore = repoRoot().resolve(".gitignore");
        assertTrue(Files.exists(gitignore), "应能从模块目录上溯到仓库 .gitignore：" + gitignore);

        String content = Files.readString(gitignore, StandardCharsets.UTF_8);
        assertTrue(content.lines()
                        .map(String::trim)
                        .filter(line -> !line.startsWith("#"))
                        .anyMatch(line -> line.equals(taskDir + "/") || line.equals("/" + taskDir + "/")),
                ".gitignore 缺少 " + taskDir + "/ 条目（" + gitignore + "）");
    }

    @Test
    @DisplayName("占位默认值解析：${X:default} 取 default，裸值原样返回")
    void placeholderParsingIsExact() {
        assertEquals("task-files", defaultValue("${JFFAST_TASK_FILE_DIR:task-files}"));
        assertEquals("task-files", defaultValue("task-files"));
    }
}
