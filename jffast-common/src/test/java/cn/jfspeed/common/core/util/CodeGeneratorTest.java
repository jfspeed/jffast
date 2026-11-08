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
package cn.jfspeed.common.core.util;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 代码生成器产物回归测试。
 *
 * <p>为什么存在：生成器产出的 Controller / DTO / 菜单 SQL 是「新模块能不能跑起来」的第一道闸，
 * 但它们此前完全没有测试护栏。以下两条是已经真实发生过的缺陷，且都只在运行期/门禁时才暴露：
 * <ul>
 *   <li><b>Request DTO 缺 id</b>：update 端点用 {@code @DistributedLock(key = "'x:' + #req.id")}
 *       并依赖 {@code service.updateById(entity)}，DTO 无 id 时 SpEL 求值抛
 *       {@code SpelEvaluationException} → 修改操作直接 500。</li>
 *   <li><b>Controller 缺 @SaCheckPermission</b>：生成的新模块端点会在
 *       {@code EndpointPermissionAnnotationTest} 权限硬门禁上 FAIL。</li>
 * </ul>
 * 数据源用 Mockito 桩（不引入 H2 等新依赖）：只喂 {@code getColumns}/{@code getTables} 的元数据，
 * 不落盘、不连库。
 *
 * @author jffast
 */
class CodeGeneratorTest {

    private static final String TABLE = "demo_example";
    private static final String PKG = "demo";

    /** 与 generateController 同源推导：frontendModule = packageName，pageName = 类名首字母小写 */
    private static final String PERM_PREFIX = "demo:demoExample:";

    @Test
    @DisplayName("crud：DTO 必须含 id，Controller 必须带 @SaCheckPermission，菜单 perms 与 Controller 同源")
    void crudArtifactsMustBeGuarded() throws Exception {
        CodeGenerator.GeneratedCode code = newGenerator().generate(TABLE, PKG, "jffast", false);

        // —— DTO：无条件生成，且 id 必须在（update 的 #req.id / updateById 依赖主键）——
        assertNotNull(code.dtoRequest(), "Request DTO 必须无条件生成（Controller 已引用它）");
        assertTrue(code.dtoRequest().contains("public class DemoExampleRequest {"),
                "DTO 类名应为 DemoExampleRequest");
        assertTrue(code.dtoRequest().contains("private Long id;"),
                "Request DTO 必须包含 id 字段，否则 update 的 @DistributedLock(#req.id) 会 500");
        assertNull(code.dtoQueryRequest(), "includeDto=false 时不应生成 QueryRequest");

        // —— Controller：四个写读端点都必须有权限码，且 update 的锁 key 用 #req.id ——
        String controller = code.controller();
        assertTrue(controller.contains("@RequestBody @Valid DemoExampleRequest req"),
                "create/update 应接收 @RequestBody @Valid Request DTO");
        assertTrue(controller.contains("#req.id"), "update 的分布式锁 key 应引用 #req.id");
        for (String action : List.of("view", "add", "update", "delete")) {
            assertTrue(controller.contains("\"" + PERM_PREFIX + action + "\""),
                    "Controller 缺少 @SaCheckPermission 权限码：" + PERM_PREFIX + action);
        }

        // —— 菜单 SQL：按钮 perms 必须与 Controller 的权限码完全同源，否则「后端要权限、菜单没配」即无权 ——
        String menuSql = code.menuSql();
        for (String action : List.of("view", "add", "update", "delete")) {
            assertTrue(menuSql.contains("'" + PERM_PREFIX + action + "'"),
                    "菜单 SQL 缺少按钮 perms：" + PERM_PREFIX + action);
        }
        assertTrue(menuSql.contains("'C'"), "菜单 SQL 应包含一个菜单(C)行");
        assertTrue(menuSql.contains("'F'"), "菜单 SQL 应包含按钮(F)行");
    }

    @Test
    @DisplayName("生成实体：@IgnoreBaseField 取 common.core.annotation，不得引用废弃的 mybatis 注解（R-05）")
    void entityIgnoreAnnotationUsesCommonPackage() throws Exception {
        for (String genType : List.of("crud", "tree")) {
            CodeGenerator.GeneratedCode code = newGenerator()
                    .generate(TABLE, PKG, "jffast", false, genType, null, null, null, null, null);
            assertTrue(code.entity().contains("import cn.jfspeed.common.core.annotation.IgnoreBaseField;"),
                    genType + " 实体必须导入 common.core.annotation.IgnoreBaseField");
            assertFalse(code.entity().contains("cn.jfspeed.mybatis.annotation.IgnoreBaseField"),
                    genType + " 实体不得再引用已废弃的 mybatis 注解");
        }
    }

    @Test
    @DisplayName("crud：includeDto=true 时额外生成 QueryRequest")
    void queryRequestGeneratedWhenIncludeDto() throws Exception {
        CodeGenerator.GeneratedCode code = newGenerator().generate(TABLE, PKG, "jffast", true);
        assertNotNull(code.dtoQueryRequest(), "includeDto=true 时应生成 QueryRequest");
        assertTrue(code.dtoQueryRequest().contains("public class DemoExampleQueryRequest {"));
    }

    @Test
    @DisplayName("tree：Controller 同样带 @SaCheckPermission，DTO 同样含 id，删除用 R.error（可编译）")
    void treeArtifactsMustBeGuarded() throws Exception {
        CodeGenerator.GeneratedCode code = newGenerator()
                .generate(TABLE, PKG, "jffast", false, "tree", null, null, null, null, null);

        assertEquals("tree", code.genType());
        assertTrue(code.dtoRequest().contains("private Long id;"));
        for (String action : List.of("view", "add", "update", "delete")) {
            assertTrue(code.controller().contains("\"" + PERM_PREFIX + action + "\""),
                    "树表 Controller 缺少 @SaCheckPermission 权限码：" + PERM_PREFIX + action);
        }
        assertTrue(code.menuSql().contains("'" + PERM_PREFIX + "delete'"),
                "树表菜单 SQL 缺少删除按钮 perms");
        // R.error 是框架真实 API；早期生成的是 R.fail，产物根本编译不过
        assertTrue(code.controller().contains("R.error(\"存在子节点"),
                "树表删除校验应使用 R.error（R.fail 不存在，产物无法编译）");
        assertFalse(code.controller().contains("R.fail("),
                "树表 Controller 不应再出现 R.fail（框架无此方法）");
    }

    @Test
    @DisplayName("masterDetail：主表 Controller 的 create/update 必须 @Valid 接收 CreateRequest")
    void masterDetailControllerUsesValidDto() throws Exception {
        CodeGenerator.GeneratedCode code = newGenerator()
                .generate(TABLE, PKG, "jffast", false, "masterDetail", "parent_id", "name",
                        "demo_item", "example_id", null);

        String controller = code.controller();
        assertTrue(controller.contains("import jakarta.validation.Valid;"),
                "主子表 Controller 必须导入 jakarta.validation.Valid");
        assertTrue(controller.contains("@RequestBody @Valid DemoExampleCreateRequest request"),
                "masterDetail 的 create/update 应以 @Valid 接收 CreateRequest");
        // Boot 4 只有 jakarta 注解：模板曾用 @javax.annotation.Resource 且缺 SaCheckPermission import，
        // 产物编译必失败（2026-10-13 评审实证）。
        assertTrue(controller.contains("import cn.dev33.satoken.annotation.SaCheckPermission;"),
                "主子表 Controller 用了 @SaCheckPermission 就必须有对应 import，否则产物编译失败");
        assertFalse(controller.contains("javax.annotation"),
                "Boot 4 下模板不得出现 javax.annotation（应为 jakarta）");
        assertTrue(code.service().contains("@jakarta.annotation.Resource"),
                "主子表 Service 必须用 @jakarta.annotation.Resource（javax 在 Boot 4 不存在）");
        assertFalse(code.service().contains("javax.annotation"),
                "主子表 Service 不得残留 javax.annotation");
    }

    @Test
    @DisplayName("数据库注释含引号：实体/DTO 走 Java 转义，建表 SQL 走 SQL 转义（产物可编译）")
    void commentWithQuotesIsEscaped() throws Exception {
        CodeGenerator generator = newGeneratorWithColumns(List.of(
                column("id", "BIGINT", 20, "主键"),
                column("c_dq", "VARCHAR", 50, "含\"双引号\""),
                column("c_sq", "VARCHAR", 50, "含'单引号'")
        ));
        CodeGenerator.GeneratedCode code = generator.generate(TABLE, PKG, "jffast", false);

        assertTrue(code.entity().contains("含\\\"双引号\\\""),
                "实体注释里的双引号必须转义，否则生成的 @Schema 字面量被截断、产物编译失败");
        assertTrue(code.dtoRequest().contains("含\\\"双引号\\\""),
                "Request DTO 注释里的双引号同样必须转义");
        assertTrue(code.sql().contains("含''单引号''"),
                "建表 SQL 注释里的单引号必须转义为 ''，否则 COMMENT 语句语法错误");
    }

    @Test
    @DisplayName("模块名/子表名走路径穿越防护：../ 之类直接拒绝，不得越出业务目录")
    void createModuleRejectsPathTraversal() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> newGenerator().createModule("../evil"));
        assertThrows(IllegalArgumentException.class, () -> newGenerator().createModule("a/b"));
        assertThrows(IllegalArgumentException.class, () -> newGenerator().createModule("Upper"));
    }

    @Test
    @DisplayName("包名留空：落回 demo，不得拼出非法包 cn.jfspeed..（双点）")
    void blankPackageFallsBackToDemo() throws Exception {
        CodeGenerator generator = newGenerator();
        for (String blank : new String[]{null, "", "   "}) {
            CodeGenerator.GeneratedCode code = generator.generate(TABLE, blank, "jffast", false);
            assertTrue(code.entity().contains("package cn.jfspeed.demo.entity;"),
                    "空包名必须兜底为 demo，实际: " + firstLine(code.entity()));
            assertFalse(code.entity().contains("cn.jfspeed.."),
                    "空包名不得拼出双点非法包名 cn.jfspeed..");
            assertTrue(code.controller().contains("package cn.jfspeed.demo.controller;"),
                    "Controller 包名同样兜底为 demo");
        }
    }

    @Test
    @DisplayName("前端根按当前后端仓名解析：带 -pro 后缀必须落到 *-ui-pro，不得落到普通 *-ui")
    void frontendRootFollowsBackendRepoName(@TempDir Path tempDir) throws Exception {
        createDirs(tempDir, "app", "app-ui", "app-pro", "app-ui-pro");

        assertEquals(tempDir.resolve("app-ui").toAbsolutePath().toString(),
                CodeGenerator.resolveFrontendRoot(tempDir.resolve("app").toFile(), null),
                "普通后端仓应解析到同级 *-ui");
        assertEquals(tempDir.resolve("app-ui-pro").toAbsolutePath().toString(),
                CodeGenerator.resolveFrontendRoot(tempDir.resolve("app-pro").toFile(), null),
                "带 -pro 后缀的后端仓应解析到同级 *-ui-pro，落到普通 *-ui 就是写进了另一个前端仓");
        assertNull(CodeGenerator.resolveFrontendRoot(tempDir.resolve("app-demo").toFile(), null),
                "没有对应前端仓时应返回 null，不得回退到任意同名仓");
    }

    @Test
    @DisplayName("前端根支持显式配置：非标准布局走 jffast.codegen.frontend-root")
    void frontendRootHonoursConfiguredOverride(@TempDir Path tempDir) throws Exception {
        Path backend = tempDir.resolve("backend");
        Path frontend = tempDir.resolve("my-ui");
        Files.createDirectories(backend);
        Files.createDirectories(frontend);
        assertEquals(frontend.toAbsolutePath().toString(),
                CodeGenerator.resolveFrontendRoot(backend.toFile(), frontend.toString()));
    }

    @Test
    @DisplayName("运行期解析（当前真实布局）：前端根与当前后端仓匹配")
    void liveFrontendRootMatchesCurrentRepo() {
        File backendRoot = new File(CodeGenerator.getProjectRoot());
        String backendName = backendRoot.getName();
        String expectedName = backendName.endsWith("-pro")
                ? backendName.substring(0, backendName.length() - "-pro".length()) + "-ui-pro"
                : backendName + "-ui";
        File expectedDir = new File(backendRoot.getParentFile(), expectedName);
        Assumptions.assumeTrue(expectedDir.isDirectory(),
                "当前布局没有同级前端仓 " + expectedDir + "，跳过运行期断言");
        assertEquals(expectedDir.getAbsolutePath(), CodeGenerator.getFrontendProjectRoot());
    }

    @Test
    @DisplayName("同源守门人：manifest / save-local 真实落盘 / zip 条目的相对路径集合必须完全相同")
    void manifestLocalAndZipShareSamePaths(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("ws");
        Path backend = root.resolve("app");
        Path frontend = root.resolve("app-ui");
        Files.createDirectories(backend.resolve("jffast-biz/jffast-demo"));
        Files.createDirectories(frontend.resolve("src"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        CodeGenerator generator = newGenerator();
        String table = "example"; // 表名会过 SAFE_SEGMENT 白名单（下划线会被拒，见报告）
        String genType = "crud";
        List<String> types = null; // null = 全部文件

        CodeGenerator.GeneratedCode code = generator.generate(table, PKG, "jffast", true,
                genType, null, null, null, null, null);
        List<CodeGenerator.FileEntry> manifest = generator.buildManifest(code, table, PKG, "jffast",
                types, true, genType, null, null, null, backend.toFile(), frontend.toFile());
        Map<String, String> saved = generator.saveToLocal(table, PKG, "jffast", types, true, true, genType,
                null, null, null, null, null, null, backend.toFile(), frontend.toFile());
        byte[] zip = generator.buildZip(table, PKG, "jffast", types, true, genType,
                null, null, null, null, null, null, backend.toFile(), frontend.toFile());

        Set<String> manifestPaths = new TreeSet<>();
        manifest.forEach(entry -> manifestPaths.add(entry.relativePath()));
        Set<String> savedPaths = relativePaths(saved.values(), root);
        Set<String> zipPaths = zipEntryNames(zip);

        System.out.println("ZIP 条目清单：" + zipPaths);
        System.out.println("save-local 落盘：" + savedPaths);

        assertEquals(manifestPaths, zipPaths, "zip 条目必须与清单构建器同源");
        assertEquals(manifestPaths, savedPaths, "save-local 落盘必须与清单构建器同源");
        assertEquals(zipPaths, savedPaths, "zip 条目与 save-local 真实落盘必须逐条一致");

        String backendBase = "app/jffast-biz/jffast-demo";
        String frontendBase = "app-ui/src";
        assertTrue(zipPaths.contains(backendBase + "/src/main/java/cn/jfspeed/demo/entity/Example.java"));
        assertTrue(zipPaths.contains(backendBase + "/src/main/resources/sql/example_menu.sql"),
                "菜单 SQL 应落在模块的 src/main/resources/sql/ 下");
        assertTrue(zipPaths.contains(frontendBase + "/api/demo.js"));
        assertTrue(zipPaths.contains(frontendBase + "/router/modules/demo.ts"), "zip 必须包含 frontendRouter");
        assertTrue(zipPaths.contains(frontendBase + "/pages/demo/example/index.vue"));
        assertTrue(zipPaths.contains(frontendBase + "/pages/demo/example/save.vue"));
        assertFalse(zipPaths.stream().anyMatch(p -> p.startsWith("src/main/java")),
                "不得再出现无后端仓名前缀的旧布局");
    }

    @Test
    @DisplayName("masterDetail：child* 全部文件进入清单与 zip，并与本地落盘路径一致")
    void masterDetailIncludesAllChildFiles(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("ws");
        Path backend = root.resolve("app");
        Path frontend = root.resolve("app-ui");
        Files.createDirectories(backend.resolve("jffast-biz/jffast-demo"));
        Files.createDirectories(backend.resolve("jffast-biz/jffast-detail"));
        Files.createDirectories(frontend.resolve("src"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        CodeGenerator generator = newGenerator();
        String table = "example";
        String childTable = "subitem";
        String genType = "masterDetail";

        CodeGenerator.GeneratedCode code = generator.generate(table, PKG, "jffast", false, genType,
                null, null, childTable, "example_id", "detail");
        List<CodeGenerator.FileEntry> manifest = generator.buildManifest(code, table, PKG, "jffast",
                null, false, genType, childTable, "detail", null, backend.toFile(), frontend.toFile());
        Map<String, String> saved = generator.saveToLocal(table, PKG, "jffast", null, true, false, genType,
                null, null, childTable, "example_id", "detail", null, backend.toFile(), frontend.toFile());
        byte[] zip = generator.buildZip(table, PKG, "jffast", null, false, genType,
                null, null, childTable, "example_id", "detail", null, backend.toFile(), frontend.toFile());

        Set<String> manifestPaths = new TreeSet<>();
        manifest.forEach(entry -> manifestPaths.add(entry.relativePath()));
        Set<String> savedPaths = relativePaths(saved.values(), root);
        Set<String> zipPaths = zipEntryNames(zip);

        assertEquals(manifestPaths, zipPaths, "主子表 zip 条目必须与清单同源");
        assertEquals(zipPaths, savedPaths, "主子表 zip 条目与 save-local 落盘必须逐条一致");

        String childBackend = "app/jffast-biz/jffast-detail/src/main/java/cn/jfspeed/detail";
        assertTrue(zipPaths.contains(childBackend + "/entity/Subitem.java"));
        assertTrue(zipPaths.contains(childBackend + "/mapper/SubitemMapper.java"));
        assertTrue(zipPaths.contains(childBackend + "/service/SubitemService.java"));
        assertTrue(zipPaths.contains(childBackend + "/controller/SubitemController.java"));
        assertTrue(zipPaths.contains("app-ui/src/api/detail.js"));
        assertTrue(zipPaths.contains("app-ui/src/pages/detail/subitem/index.vue"));
        assertTrue(zipPaths.contains("app-ui/src/pages/detail/subitem/save.vue"));
    }

    @Test
    @DisplayName("主子表同模块：父子 API 合并为单个 zip 条目，不得因重复条目打包失败")
    void masterDetailSamePackageMergesApiEntry(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("ws");
        Path backend = root.resolve("app");
        Path frontend = root.resolve("app-ui");
        Files.createDirectories(backend.resolve("jffast-biz/jffast-demo"));
        Files.createDirectories(frontend.resolve("src"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        CodeGenerator generator = newGenerator();
        String table = "example";
        String childTable = "subitem";
        String genType = "masterDetail";

        // childPackageName 留空 → 子表与主表同包，父子 API 落在同一个 demo.js
        byte[] zip = generator.buildZip(table, PKG, "jffast", null, false, genType,
                null, null, childTable, "example_id", null, null, backend.toFile(), frontend.toFile());

        Map<String, String> entries = zipContents(zip);
        String api = entries.get("app-ui/src/api/demo.js");
        assertNotNull(api, "父子同模块时必须只有一个 demo.js 条目");
        assertTrue(api.contains("const example = createCrud('/demo/example')"), "主表 API 不得丢");
        assertTrue(api.contains("const subitem = createCrud('/demo/subitem')"), "子表 API 不得丢");
        assertTrue(api.contains("export default { example, subitem };"), "导出对象必须合并");
        assertFalse(api.contains("export default { example };\nexport default"), "不得残留两个 export default");
    }


    @Test
    @DisplayName("表名白名单：sys_user / biz_order_item 放行，大写、数字开头、路径字符一律拒绝且落点不越界")
    void tableNameWhitelistAllowsUnderscore(@TempDir Path tempDir) throws Exception {
        Path root = tempDir.resolve("ws");
        Path backend = root.resolve("app");
        Path frontend = root.resolve("app-ui");
        Files.createDirectories(backend.resolve("jffast-biz/jffast-demo"));
        Files.createDirectories(frontend.resolve("src"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        CodeGenerator generator = newGenerator();
        Path backendRoot = backend.toAbsolutePath().normalize();
        Path frontendRoot = frontend.toAbsolutePath().normalize();

        for (String table : List.of("sys_user", "biz_order_item")) {
            Map<String, String> saved = generator.saveToLocal(table, PKG, "jffast", null, true, true, "crud",
                    null, null, null, null, null, null, backend.toFile(), frontend.toFile());
            assertFalse(saved.isEmpty(), table + " 应通过校验并落盘");
            assertTrue(relativePaths(saved.values(), root)
                            .contains("app/jffast-biz/jffast-demo/src/main/resources/sql/" + table + "_menu.sql"),
                    "菜单 SQL 文件名应含合法表名：" + table);
            saved.values().forEach(path -> {
                Path absolute = Path.of(path).toAbsolutePath().normalize();
                assertTrue(absolute.startsWith(backendRoot) || absolute.startsWith(frontendRoot),
                        "落点必须留在后端仓或前端仓内，不得越界：" + path);
            });
        }

        Set<String> entries = zipEntryNames(generator.buildZip("sys_user", PKG, "jffast", null, true, "crud",
                null, null, null, null, null, null, backend.toFile(), frontend.toFile()));
        assertTrue(entries.contains("app/jffast-biz/jffast-demo/src/main/resources/sql/sys_user_menu.sql"),
                "ZIP 应包含下划线表名的菜单 SQL");
        assertTrue(entries.stream().noneMatch(name -> name.contains("..") || name.startsWith("/")),
                "ZIP 条目不得出现路径穿越： " + entries);

        for (String invalid : List.of("Sys_User", "1abc", "a/b", "a..b", "a b", "../evil", "a\\b", "..")) {
            assertThrows(IllegalArgumentException.class, () -> generator.saveToLocal(invalid, PKG, "jffast",
                    null, true, true, "crud", null, null, null, null, null, null,
                    backend.toFile(), frontend.toFile()), "表名应被拒绝：" + invalid);
        }
    }


    /** 把本地落盘的绝对路径归一化成「相对后端仓与前端仓的同级父目录」的相对路径，与 zip 条目名同一坐标系 */
    private static Set<String> relativePaths(Iterable<String> absolutePaths, Path workspaceRoot) {
        Set<String> result = new TreeSet<>();
        Path root = workspaceRoot.toAbsolutePath().normalize();
        for (String path : absolutePaths) {
            result.add(root.relativize(Path.of(path).toAbsolutePath().normalize()).toString().replace('\\', '/'));
        }
        return result;
    }

    private static Set<String> zipEntryNames(byte[] zip) throws Exception {
        return new TreeSet<>(zipContents(zip).keySet());
    }

    private static Map<String, String> zipContents(byte[] zip) throws Exception {
        Map<String, String> contents = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                contents.put(entry.getName().replace('\\', '/'), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    private static void createDirs(Path base, String... names) throws Exception {
        for (String name : names) {
            Files.createDirectories(base.resolve(name));
        }
    }

    private static String firstLine(String source) {
        int idx = source == null ? -1 : source.indexOf('\n');
        return idx < 0 ? String.valueOf(source) : source.substring(0, idx);
    }

    // ------------------------------------------------------------------
    // Mockito 桩：只提供 getColumns / getTables 元数据；prepareStatement 返回 null
    // → generateMenuSql 走 catch 分支取默认 parentId/sort（不影响权限码断言）。
    // ------------------------------------------------------------------

    private CodeGenerator newGenerator() throws Exception {
        return newGeneratorWithColumns(List.of(
                column("id", "BIGINT", 20, "主键"),
                column("name", "VARCHAR", 50, "名称"),
                column("status", "TINYINT", 4, "状态"),
                column("remark", "VARCHAR", 255, "备注"),
                column("tenant_id", "BIGINT", 20, "租户ID"),
                column("create_time", "DATETIME", 0, "创建时间")
        ));
    }

    private CodeGenerator newGeneratorWithColumns(List<Map<String, Object>> columns) throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(conn.getCatalog()).thenReturn("jffast_test");
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getTables(any(), any(), any(), any())).thenAnswer(inv -> tableResultSet());
        when(meta.getColumns(any(), any(), any(), any())).thenAnswer(inv -> resultSet(columns));

        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(conn);

        CodeGenerator generator = new CodeGenerator();
        generator.setDataSource(ds);
        return generator;
    }

    private ResultSet tableResultSet() throws Exception {
        return resultSet(List.of(Map.of("TABLE_NAME", TABLE, "REMARKS", "示例表")));
    }

    private Map<String, Object> column(String name, String type, int size, String comment) {
        return Map.of("COLUMN_NAME", name, "TYPE_NAME", type, "COLUMN_SIZE", size, "REMARKS", comment);
    }

    private ResultSet resultSet(List<Map<String, Object>> rows) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        AtomicInteger idx = new AtomicInteger(-1);
        when(rs.next()).thenAnswer(inv -> idx.incrementAndGet() < rows.size());
        when(rs.getString(anyString())).thenAnswer(inv -> {
            Object value = rows.get(idx.get()).get(inv.getArgument(0, String.class));
            return value == null ? null : String.valueOf(value);
        });
        when(rs.getInt(anyString())).thenAnswer(inv -> {
            Object value = rows.get(idx.get()).get(inv.getArgument(0, String.class));
            return value instanceof Number number ? number.intValue() : 0;
        });
        return rs;
    }
}