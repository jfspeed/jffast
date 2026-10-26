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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码生成器命令行入口：不启动应用、不依赖 Spring 容器，直接生成整个模块。
 * <p>
 * 目标是「AI / 新人写模块的最短路径」——一条命令产出 DTO + Controller + Service +
 * Mapper + Entity + 前端三件套 + 菜单 SQL，且产物即合规（Controller 走 Request DTO，
 * 与 {@code DemoExampleController} 模板一致）。
 *
 * <h2>用法（在目标工程根目录运行）</h2>
 * <pre>
 * mvn -q -pl jffast-common -am compile exec:java \
 *   -Dexec.mainClass=cn.jfspeed.common.core.util.CodeGeneratorCLI \
 *   -Dexec.args="--url=jdbc:mysql://127.0.0.1:3306/jffast --user=root --password=root \
 *                --table=sys_price --package=system --author=zhang"
 * </pre>
 * 树表：追加 {@code --tree --parent-field=parent_id --name-field=name}
 * <br>主子表：{@code --gen-type=masterDetail --child-table=... --child-fk=...}
 *
 * <p>注意：写入位置由 {@link CodeGenerator#getProjectRoot()} 按运行目录推断，
 * 必须在 Maven 多模块工程根目录执行。
 *
 * @author jffast
 */
public final class CodeGeneratorCLI {

    private CodeGeneratorCLI() {
    }

    public static void main(String[] args) {
        Map<String, String> opts = parseArgs(args);

        String url = require(opts, "url");
        String user = opts.getOrDefault("user", "root");
        String password = opts.getOrDefault("password", "");
        String table = require(opts, "table");
        String pkg = require(opts, "package");
        String author = opts.getOrDefault("author", "jffast");
        String genType = opts.getOrDefault("gen-type", opts.containsKey("tree") ? "tree" : "crud");
        String module = opts.get("module");

        CodeGenerator generator = new CodeGenerator();
        generator.setDataSource(simpleDataSource(url, user, password));

        Map<String, String> saved = generator.saveToLocal(
                table,
                pkg,
                author,
                null,   // types：null = 全部生成
                true,   // overwrite：CLI 场景明确覆盖，避免静默半成品
                true,   // includeDto：额外生成 QueryRequest（Request 本身已无条件生成）
                genType,
                opts.get("parent-field"),
                opts.get("name-field"),
                opts.get("child-table"),
                opts.get("child-fk"),
                opts.get("child-package"),
                module);

        System.out.println("== 生成完成（" + genType + "）==");
        saved.forEach((type, path) -> System.out.println("  " + type + " -> " + path));
        System.out.println("提示：新模块接线见对应 AGENTS.md（topPom <module> + sys_menu 种子）");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("参数必须以 -- 开头：" + arg);
            }
            int eq = arg.indexOf('=');
            if (eq < 0) {
                // 布尔开关，如 --tree
                opts.put(arg.substring(2), "true");
            } else {
                opts.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        return opts;
    }

    private static String require(Map<String, String> opts, String key) {
        String value = opts.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数 --" + key);
        }
        return value;
    }

    /**
     * 最小化 DataSource 实现：只支持 getConnection，避免 CLI 依赖连接池。
     * 驱动通过 JDBC SPI 自动加载（classpath 里有 mysql-connector 即可）。
     */
    private static DataSource simpleDataSource(String url, String user, String password) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url, user, password);
            }

            @Override
            public Connection getConnection(String username, String pwd) throws SQLException {
                return DriverManager.getConnection(url, username, pwd);
            }

            @Override
            public <T> T unwrap(Class<T> iface) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }

            @Override
            public java.io.PrintWriter getLogWriter() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setLogWriter(java.io.PrintWriter out) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setLoginTimeout(int seconds) {
                DriverManager.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() {
                return DriverManager.getLoginTimeout();
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
