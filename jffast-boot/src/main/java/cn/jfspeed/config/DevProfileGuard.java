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
package cn.jfspeed.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期 profile 守卫：未显式指定 prod 时，把当前生效的开发态暴露面大声播报出来；
 * 并对「随仓库公开的弱口令 + 指向非本机的服务端点」这一组合直接拒绝启动。
 *
 * <p><b>为什么要有（暴露面播报）</b>：仓库设置 {@code spring.profiles.default=dev}，是为了让「clone 即可零配置
 * 跑起来」（本地体验是开源框架的卖点）。但同一份配置里带着开发态默认值：admin 初始口令是**随仓库
 * 公开的测试值**、Swagger 默认开放、数据库与 Redis 指向本机默认地址与口令、日志级别为 dev。
 * 裸跑 {@code java -jar} 而忘了 {@code SPRING_PROFILES_ACTIVE=prod} 时，服务会**静默**以这些
 * 默认值起在服务器上——没有任何报错，只有事后被人发现。
 *
 * <p><b>为什么不直接改成启动失败</b>：那会毁掉「零配置本地可跑」这一开箱体验。本守卫的定位是
 * <b>把风险显式化</b>：起在 dev 时在启动日志里逐条列出暴露项与生产部署的正确姿势。真正需要
 * fail-fast 的形态（容器）已有更硬的把关——{@code jffast-boot/Dockerfile} 的 ENTRYPOINT 在
 * {@code SPRING_PROFILES_ACTIVE} 未设置时直接 exit 1。
 *
 * <p><b>弱口令 fail-fast（硬拦截）</b>：上面那套「播报」挡不住一种具体事故——
 * <b>弱口令被搬到远程端点上</b>。典型姿势：开发机连远程 MySQL 时忘了改口令，于是
 * 「随仓库公开的 {@code test1234}」变成了<em>远程数据库的真实口令</em>；或者 admin 初始口令
 * 仍是公开值，而服务连的是公网数据库 ⇒ 任何人 clone 仓库即可登录管理后台。
 * 这类事故没有任何报错，只有被入侵之后才发现。
 *
 * <p>因此本守卫加了一条硬规则：
 * <b>只要仍使用随仓库公开的默认口令，且数据源 / Redis 指向非本机地址，就拒绝启动</b>。
 * 三条判据都命中才拦（避免误伤正常的零配置本机开发）：
 * <ol>
 *   <li>有效口令仍是公开默认值（{@code jffast.system.init-password} / {@code spring.datasource.password}
 *       / {@code spring.data.redis.password} 任一等于它）；</li>
 *   <li>对应的服务端点是<em>远程</em>的（非 127.0.0.1 / localhost / ::1）；</li>
 *   <li>当前不是 {@code demo} profile（演示站是显式对外展示形态，见下方豁免说明）。</li>
 * </ol>
 *
 * <p><b>demo profile 豁免</b>：{@code application-demo.yml} 只描述「演示模式」这一层，数据源来自
 * dev，而演示站按设计就是要连一台远程机器；同时它的写操作全部被短路（不入库）。若对它同样
 * fail-fast，等于直接废掉 README 对外宣传的演示能力。因此 demo 只打 ERROR 级告警，不拦启动。
 *
 * <p>prod profile 下 {@code application-prod.yml} 已把 RSA 密钥、初始口令、数据库口令等
 * 全部改为必填（缺失即启动失败），本守卫在 prod 下不输出任何提示。
 *
 * @author jffast
 */
@Slf4j
@Component
public class DevProfileGuard implements ApplicationRunner {

    /** 以 prod 激活时视为生产部署，不再提示 */
    private static final String PROD = "prod";

    /** 演示站 profile：显式对外展示形态，只告警不拦截 */
    private static final String DEMO = "demo";

    /**
     * 随仓库公开的统一测试填充值（README / application.yml / docker-compose 一致）。
     * 它本身不是缺陷——问题是它被用在了**远程端点**上。
     */
    static final String PUBLIC_TEST_PASSWORD = "test1234";

    /** jdbc:&lt;driver&gt;://host:port/db?params —— 取出其中的 host；IPv6 形如 [::1] 整体匹配后再剥壳 */
    private static final Pattern JDBC_HOST_PATTERN = Pattern.compile("jdbc:[a-zA-Z0-9]+://(\\[[^\\]]+\\]|[^:/?]+)");

    /** 视为「本机」的地址 */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final Environment environment;

    public DevProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> profiles = effectiveProfiles();

        // ---- 硬拦截段：弱口令 + 远程端点 ----
        List<String> findings = collectWeakCredentialFindings(
                environment.getProperty("jffast.system.init-password", ""),
                environment.getProperty("spring.datasource.password", ""),
                environment.getProperty("spring.data.redis.password", ""),
                environment.getProperty("spring.datasource.url", ""),
                environment.getProperty("spring.data.redis.host", ""));
        if (!findings.isEmpty()) {
            if (profiles.contains(DEMO)) {
                log.error("================================================================");
                log.error("[严重] 演示站（demo profile）当前处于弱口令暴露状态：");
                findings.forEach(f -> log.error("  · {}", f));
                log.error("演示站属对外展示形态，故不阻断启动；但请务必在正式对外前更换这些口令。");
                log.error("================================================================");
            } else {
                throw new IllegalStateException(buildRejectionMessage(findings, profiles));
            }
        }

        if (profiles.contains(PROD)) {
            return;
        }

        String profileText = profiles.isEmpty()
                ? "(未显式指定，spring.profiles.default=dev)"
                : String.join(",", profiles);
        log.warn("================================================================");
        log.warn("当前以**开发态**启动（激活 profile：{}）", profileText);
        log.warn("开发态意味着以下默认行为在生效，请勿直接用于生产部署：");
        log.warn("  1. admin 初始口令为随仓库公开的测试值（jffast.system.init-password 默认 {}）", PUBLIC_TEST_PASSWORD);
        log.warn("  2. 接口文档（Swagger UI）默认开放：/jffast-api/swagger-ui.html");
        log.warn("  3. 数据源与 Redis 使用本机默认地址与默认口令（见 application-dev.yml）");
        log.warn("  4. 日志级别为 dev，会打印 SQL 等调试信息");
        log.warn("生产部署请显式设置 SPRING_PROFILES_ACTIVE=prod：");
        log.warn("  · application-prod.yml 中 RSA 密钥、初始口令、数据库/Redis 口令均为必填，缺失即启动失败");
        log.warn("  · 容器形态另有硬把关：Dockerfile 在 SPRING_PROFILES_ACTIVE 未设置时直接退出");
        log.warn("================================================================");
    }

    /**
     * 判定「弱口令被搬到远程端点上」并列出具体命中项。
     *
     * <p>纯函数（不碰 Spring 上下文），便于单测直接断言各种组合。
     *
     * @param initPassword  {@code jffast.system.init-password} 的有效值
     * @param dbPassword    {@code spring.datasource.password} 的有效值
     * @param redisPassword {@code spring.data.redis.password} 的有效值
     * @param dbUrl         {@code spring.datasource.url} 的有效值
     * @param redisHost     {@code spring.data.redis.host} 的有效值
     * @return 命中项描述列表；为空表示安全
     */
    static List<String> collectWeakCredentialFindings(String initPassword, String dbPassword, String redisPassword,
                                                      String dbUrl, String redisHost) {
        List<String> findings = new ArrayList<>();
        String dbHost = extractJdbcHost(dbUrl);
        boolean dbRemote = dbHost != null && !isLocalHost(dbHost);
        boolean redisRemote = redisHost != null && !redisHost.isBlank() && !isLocalHost(redisHost);

        if (PUBLIC_TEST_PASSWORD.equals(initPassword) && (dbRemote || redisRemote)) {
            findings.add(String.format(
                    "管理员初始口令仍是随仓库公开的默认值 %s，而服务端点指向非本机地址（数据库 %s，Redis %s）"
                            + "——任何拿到仓库的人都能登录管理后台",
                    PUBLIC_TEST_PASSWORD,
                    dbRemote ? dbHost : "本机",
                    redisRemote ? redisHost : "本机"));
        }
        if (PUBLIC_TEST_PASSWORD.equals(dbPassword) && dbRemote) {
            findings.add(String.format(
                    "数据库口令仍是随仓库公开的默认值 %s，而数据源指向非本机地址（%s）", PUBLIC_TEST_PASSWORD, dbHost));
        }
        if (PUBLIC_TEST_PASSWORD.equals(redisPassword) && redisRemote) {
            findings.add(String.format(
                    "Redis 口令仍是随仓库公开的默认值 %s，而 Redis 指向非本机地址（%s）", PUBLIC_TEST_PASSWORD, redisHost));
        }
        return findings;
    }

    /**
     * 从 JDBC URL 中取出主机名；不是 JDBC URL 或格式异常时返回 null（视为「未配置」，不据此判远程）。
     */
    static String extractJdbcHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return null;
        }
        Matcher matcher = JDBC_HOST_PATTERN.matcher(jdbcUrl.trim());
        if (!matcher.find()) {
            return null;
        }
        String host = matcher.group(1);
        // jdbc:mysql://[::1]:3306/db 这类方括号写法
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host;
    }

    /** 是否为本机地址；127.x.x.x 整个回环段都算本机 */
    static boolean isLocalHost(String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        // IPv6 的方括号写法（[::1]）
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (LOCAL_HOSTS.contains(normalized)) {
            return true;
        }
        return normalized.startsWith("127.");
    }

    /**
     * 生效 profile 集合：显式激活的 + {@code spring.profiles.default}。
     *
     * <p>profile 分组（{@code spring.profiles.group.demo: [dev]}）在配置数据加载阶段就已展开进
     * 激活列表，因此这里读到的 {@code getActiveProfiles()} 已包含组内成员。
     */
    private Set<String> effectiveProfiles() {
        Set<String> profiles = new LinkedHashSet<>();
        profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
        if (profiles.isEmpty()) {
            String defaultProfile = environment.getProperty("spring.profiles.default");
            if (defaultProfile != null && !defaultProfile.isBlank()) {
                profiles.add(defaultProfile.trim());
            }
        }
        return profiles;
    }

    private String buildRejectionMessage(List<String> findings, Set<String> profiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("拒绝启动：检测到弱口令与远程端点同时存在（激活 profile：")
                .append(profiles.isEmpty() ? "(未显式指定)" : String.join(",", profiles))
                .append("）");
        for (String finding : findings) {
            sb.append(System.lineSeparator()).append("  · ").append(finding);
        }
        sb.append(System.lineSeparator())
                .append("处理方式（任选其一）：")
                .append(System.lineSeparator())
                .append("  1. 注入强口令后重启：环境变量 JFFAST_INIT_PASSWORD / MYSQL_PASSWORD / REDIS_PASSWORD；")
                .append(System.lineSeparator())
                .append("  2. 若希望随机生成初始口令，把 JFFAST_INIT_PASSWORD 置空（空值 ⇒ 随机生成强口令并打印到启动日志）；")
                .append(System.lineSeparator())
                .append("  3. 若只是想在本机跑，请把 MYSQL_HOST / REDIS_HOST 指回 127.0.0.1。");
        return sb.toString();
    }
}
