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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 种子 admin 占位口令回归测试（无需 MySQL / Redis）。
 *
 * <p>为什么存在：DataInitializer 只在种子 admin 口令为字面量 {@code "admin"} 时
 * 才会写入真正的初始口令（base 默认 {@code test1234}，pro 默认 {@code A@123456}）。
 * 2026-10-12 的 zero-config 提交只改了种子注释、漏改了 BCrypt hash 值，
 * 导致全新库的 admin 口令恒为旧 hash（A@123456），文档写的 test1234 永远登不上，
 * 且 DataInitializer 静默跳过、没有任何报错 —— 直到 2026-10-13 冷启动冒烟才暴露。
 *
 * @author jffast
 */
class SeedAdminPlaceholderTest {

    @Test
    @DisplayName("system_init_data.sql 的 admin 口令必须是占位值 admin（由 DataInitializer 替换）")
    void seedAdminPasswordMustBePlaceholder() throws Exception {
        java.nio.file.Path seed = repoRoot().resolve("sql").resolve("system_init_data.sql");
        if (!java.nio.file.Files.isRegularFile(seed)) {
            fail("找不到种子文件：" + seed);
        }
        String adminLine = null;
        for (String line : java.nio.file.Files.readAllLines(seed)) {
            // admin 行特征：id=1 且 user_name='admin'（与 real_name '管理员' 同行）
            if (line.contains("'admin', '管理员'")) {
                adminLine = line.trim();
                break;
            }
        }
        if (adminLine == null) {
            fail("种子文件中找不到 admin 用户的 INSERT 行，断言本身可能已失效");
        }
        // VALUES 列序：id, tenant_id, user_name, real_name, nick_name, dept_id, password, ...
        // password 是第 7 个逗号分隔段（均为单引号包裹或 NULL）
        String[] cols = adminLine.split("\\s*,\\s*");
        if (cols.length < 7) {
            fail("admin 行列数异常：" + adminLine);
        }
        assertEquals("'admin'", cols[6].trim(),
                "种子 admin 口令必须是占位值 'admin'（DataInitializer 只在占位时才写入真实初始口令）；"
                        + "写死 hash 会导致文档口令永远登不上且静默跳过");
    }

    private static java.nio.file.Path repoRoot() {
        // surefire 的工作目录一般是模块目录（jffast-boot），但不硬编码层级：
        // 从 cwd 逐级向上找含 sql/ 的目录。
        java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (java.nio.file.Files.isDirectory(dir.resolve("sql"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        fail("无法定位仓库根（从 " + java.nio.file.Path.of("").toAbsolutePath()
                + " 向上找不到含 sql/ 的目录）");
        throw new IllegalStateException("unreachable");
    }
}
