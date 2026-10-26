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

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期 profile 守卫：未显式指定 prod 时，把当前生效的开发态暴露面大声播报出来。
 *
 * <p><b>为什么要有</b>：仓库设置 {@code spring.profiles.default=dev}，是为了让「clone 即可零配置
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

    private final Environment environment;

    public DevProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (Arrays.asList(activeProfiles).contains(PROD)) {
            return;
        }

        String profiles = activeProfiles.length == 0 ? "(未显式指定，spring.profiles.default=dev)" : String.join(",", activeProfiles);
        log.warn("================================================================");
        log.warn("当前以**开发态**启动（激活 profile：{}）", profiles);
        log.warn("开发态意味着以下默认行为在生效，请勿直接用于生产部署：");
        log.warn("  1. admin 初始口令为随仓库公开的测试值（jffast.system.init-password 默认 test1234）");
        log.warn("  2. 接口文档（Swagger UI）默认开放：/jffast-api/swagger-ui.html");
        log.warn("  3. 数据源与 Redis 使用本机默认地址与默认口令（见 application-dev.yml）");
        log.warn("  4. 日志级别为 dev，会打印 SQL 等调试信息");
        log.warn("生产部署请显式设置 SPRING_PROFILES_ACTIVE=prod：");
        log.warn("  · application-prod.yml 中 RSA 密钥、初始口令、数据库/Redis 口令均为必填，缺失即启动失败");
        log.warn("  · 容器形态另有硬把关：Dockerfile 在 SPRING_PROFILES_ACTIVE 未设置时直接退出");
        log.warn("================================================================");
    }
}
