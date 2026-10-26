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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * sys_role 唯一性契约测试（无需 MySQL）。
 *
 * <p>为什么存在：{@code role_key} 是授权判定依据（服务层用 {@code selectOne(eq("role_key", ...))}
 * 取「普通用户角色」来生成新角色 id），一旦重名，{@code selectOne} 直接抛结果集异常，
 * 整条角色创建链变成 500。控制器的 {@code checkRoleNameAndKey} 是「先查后插」，并发下两个请求
 * 能同时通过，唯一性必须由库侧索引兜住。
 *
 * <p>索引必须是 {@code (tenant_id, role_key)} 而不是 {@code (role_key)}：不带租户维度会让
 * 商业版多租户下两个租户无法各自拥有 {@code common}。而 {@code tenant_id} 若可空，MySQL 唯一索引
 * <b>不约束 NULL 行</b> —— Base 默认不启用租户隔离、所有行 tenant_id 为 NULL，那种索引看着加了、
 * 实际一条都没管住。所以 NOT NULL DEFAULT 0 与索引是一件事，两半都要钉住，连同种子数据不能写 NULL。
 *
 * @author jffast
 */
class RoleKeyUniquenessDdlTest {

    private static final Pattern UNIQUE_KEY_COLUMNS =
            Pattern.compile("UNIQUE\\s+KEY\\s+`?\\w+`?\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private static String tableBlock(String ddl, String table) {
        int start = ddl.indexOf("CREATE TABLE `" + table + "`");
        assertTrue(start >= 0, "建表脚本里找不到 CREATE TABLE `" + table + "`，断言本身可能已失效");
        int end = ddl.indexOf("ENGINE=", start);
        assertTrue(end > start, "CREATE TABLE `" + table + "` 缺少结尾的 ENGINE 行，脚本可能被截断");
        return ddl.substring(start, end);
    }

    private static String columnLine(String block, String column) {
        for (String line : block.split("\n")) {
            if (line.contains("`" + column + "`") && !line.contains("KEY")) {
                return line.trim();
            }
        }
        fail("sys_role 建表语句里找不到列 `" + column + "`");
        return null;
    }

    @Test
    @DisplayName("sys_role.tenant_id 必须 NOT NULL DEFAULT 0（可空列在唯一索引里不受约束）")
    void tenantIdMustBeNullableFalseWithZeroDefault() {
        String block = tableBlock(readSql("system_sql.sql"), "sys_role");
        String line = columnLine(block, "tenant_id");
        assertTrue(line.toUpperCase().contains("NOT NULL"),
                "tenant_id 可空时唯一索引约束不到 NULL 行，等于没加；实际列定义：" + line);
        assertTrue(line.contains("DEFAULT 0"),
                "tenant_id 需以 0 作为「单租户/未启用租户隔离」的哨兵值；实际列定义：" + line);
    }

    @Test
    @DisplayName("sys_role 必须有且仅有一个覆盖 (tenant_id, role_key) 的唯一索引")
    void uniqueIndexMustCoverTenantIdAndRoleKey() {
        String block = tableLineJoin(tableBlock(readSql("system_sql.sql"), "sys_role"));
        List<String> uniques = new ArrayList<>();
        Matcher matcher = UNIQUE_KEY_COLUMNS.matcher(block);
        while (matcher.find()) {
            uniques.add(matcher.group(1).replaceAll("\\s", ""));
        }
        assertEquals(List.of("`tenant_id`,`role_key`"), uniques,
                "sys_role 上的唯一索引应当恰好一条且列为 (tenant_id, role_key)；实际：" + uniques
                        + "。只写 (role_key) 会挡住商业版多租户，写 (tenant_id) 或不写都管不住重名标识");
    }

    @Test
    @DisplayName("sys_role 种子行的 tenant_id 不得为 NULL（NOT NULL 列写 NULL 会让全新库引导直接失败）")
    void seedRowsMustNotInsertNullTenantId() {
        List<String> seedLines = readSqlLines("system_init_data.sql");
        int checked = 0;
        for (int i = 0; i < seedLines.size(); i++) {
            if (!seedLines.get(i).contains("INSERT INTO `sys_role`")) {
                continue;
            }
            String tuple = null;
            for (int j = i + 1; j < seedLines.size() && tuple == null; j++) {
                String candidate = seedLines.get(j).trim();
                if (candidate.isEmpty() || candidate.startsWith("--")) {
                    continue;
                }
                tuple = candidate;
            }
            assertNotNull(tuple, "INSERT INTO `sys_role` 后面找不到 VALUES 行");
            String secondColumn = tuple.replaceFirst("^\\(\\s*[^,]+,\\s*", "").split(",")[0].trim();
            assertTrue(!"NULL".equalsIgnoreCase(secondColumn),
                    "sys_role 种子行第 2 列（tenant_id）写成 NULL，会撞上 NOT NULL 约束、全新库引导失败；实际：" + tuple);
            checked++;
        }
        assertTrue(checked >= 2,
                "至少应校验到 super_admin 与 common 两条 sys_role 种子行，实际校验到 " + checked + " 条");
    }

    private static String tableLineJoin(String block) {
        // 去掉行注释：索引说明注释里也写了 UNIQUE / CREATE TABLE 等关键字，不去掉会误匹配
        StringBuilder stripped = new StringBuilder();
        for (String line : block.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            stripped.append(line).append('\n');
        }
        return stripped.toString();
    }

    private static String readSql(String fileName) {
        try {
            return Files.readString(sqlDir().resolve(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("读取 sql/" + fileName + " 失败", e);
        }
    }

    private static List<String> readSqlLines(String fileName) {
        try {
            return Files.readAllLines(sqlDir().resolve(fileName));
        } catch (IOException e) {
            throw new IllegalStateException("读取 sql/" + fileName + " 失败", e);
        }
    }

    private static Path sqlDir() {
        // surefire 的 cwd 通常是模块目录（jffast-boot），但不硬编码层级：从 cwd 逐级向上找含 sql/ 的目录
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("sql"))) {
                return dir.resolve("sql");
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 sql/ 目录（cwd=" + Path.of("").toAbsolutePath() + "）");
    }
}
