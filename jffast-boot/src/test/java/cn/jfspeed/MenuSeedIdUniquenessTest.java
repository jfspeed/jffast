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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sys_menu} 种子主键唯一性回归测试（无需 MySQL / Redis）。
 *
 * <p>为什么存在：菜单种子散在多个「可选模块」SQL 里，而模块侧用的是
 * {@code INSERT IGNORE} —— 两个文件写同一个 id 时，后导入的一方<b>整行被静默丢弃</b>，
 * 表现是「菜单永远不出现、导入也不报错」。2026-10-14 实测到三份模块菜单
 * 与核心种子撞号，只有把「跨文件 id 不重复」变成断言才防得住再次添加菜单时重演。
 *
 * @author jffast
 */
class MenuSeedIdUniquenessTest {

    /** 匹配一条 sys_menu 的 INSERT 语句（列清单 + VALUES 到语句结尾的分号）。 */
    private static final Pattern MENU_INSERT = Pattern.compile(
            "(?is)insert\\s+(?:ignore\\s+)?into\\s+`?sys_menu`?\\s*\\(([^)]*)\\)\\s*values\\s*(.*?)\\);");

    @Test
    @DisplayName("sql/ 下所有 sys_menu 种子的显式主键不得重复")
    void menuSeedIdsMustBeUnique() throws Exception {
        Path sqlDir = repoRoot().resolve("sql");
        Map<Long, List<String>> byId = new LinkedHashMap<>();
        List<String> scanned = new ArrayList<>();
        try (Stream<Path> files = Files.list(sqlDir)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList()) {
                scanned.add(file.getFileName().toString());
                String text = Files.readString(file);
                Matcher block = MENU_INSERT.matcher(text);
                while (block.find()) {
                    List<String> cols = splitTopLevel(block.group(1).replace("`", ""));
                    cols.replaceAll(s -> s.trim().toLowerCase());
                    int idIdx = cols.indexOf("id");
                    if (idIdx < 0) {
                        continue; // 未显式指定主键的 INSERT 由自增接管，不可能撞号
                    }
                    int line = 1 + (int) text.substring(0, block.start()).chars().filter(c -> c == '\n').count();
                    for (String row : block.group(2).split("\\)\\s*,\\s*\\(")) {
                        List<String> vals = splitTopLevel(stripRowParentheses(row));
                        long id = parseId(vals.get(idIdx));
                        if (id < 0) {
                            continue;
                        }
                        byId.computeIfAbsent(id, k -> new ArrayList<>())
                                .add(file.getFileName() + ":" + line + " " + nameOf(cols, vals));
                    }
                }
            }
        }
        assertTrue(scanned.size() >= 10, "sql/ 目录扫描到的文件过少，断言本身可能已失效：" + scanned);
        // 解析器一旦失效（列名改动 / 语句形态变化）就会「0 冲突」假绿，故对样本量设下限。
        assertTrue(byId.size() >= 100,
                "只解析到 " + byId.size() + " 个显式 sys_menu 主键，远低于预期（>100），说明解析规则已失效");

        List<String> conflicts = new ArrayList<>();
        byId.forEach((id, occ) -> {
            if (occ.size() > 1) {
                conflicts.add("id=" + id + " 被 " + occ.size() + " 处使用 -> " + String.join(" | ", occ));
            }
        });
        assertTrue(conflicts.isEmpty(),
                "sys_menu 种子主键冲突（后导入的一方会因 INSERT IGNORE 被静默丢弃 = 菜单缺失且无报错）：\n"
                        + String.join("\n", conflicts));
    }

    /** VALUES 首行带开括号、末行带闭括号（切分只去掉行间的 `), (`），统一剥掉避免误解析。 */
    private static String stripRowParentheses(String row) {
        String s = row.trim();
        if (s.startsWith("(")) {
            s = s.substring(1);
        }
        int end = s.lastIndexOf(')');
        return end >= 0 ? s.substring(0, end) : s;
    }

    /** 行内 name 列的值，仅用于失败信息可读；缺列时返回空串。 */
    private static String nameOf(List<String> cols, List<String> vals) {
        int i = cols.indexOf("name");
        return i < 0 || i >= vals.size() ? "" : vals.get(i).replace("'", "");
    }

    private static long parseId(String raw) {
        String s = raw.trim().replace("`", "").replace("'", "");
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1; // NULL / 函数表达式等非显式数字主键
        }
    }

    /** 按顶层逗号切分，忽略单/双引号内的逗号。 */
    private static List<String> splitTopLevel(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
                cur.append(c);
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
                cur.append(c);
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static Path repoRoot() {
        // surefire 工作目录一般是模块目录，不硬编码层数：从 cwd 逐级向上找含 sql/ 的目录。
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("sql"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到含 sql/ 的仓库根（cwd=" + Path.of("").toAbsolutePath() + "）");
    }
}
