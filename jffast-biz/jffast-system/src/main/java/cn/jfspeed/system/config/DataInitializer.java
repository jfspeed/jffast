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
package cn.jfspeed.system.config;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.security.util.EncryptionUtil;
import cn.jfspeed.common.security.util.PasswordGenerator;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时数据初始化：为 seed 数据里的 admin 账号写入真正的初始密码。
 * <p>
 * seed 脚本里 admin 的 password 是占位值 {@code "admin"}，此处将其替换为加密后的真实口令。
 * 因为写库后该字段不再是占位值，本逻辑天然只执行一次，重启不会反复改密码。
 * <p>
 * <b>为什么住在本模块（{@code jffast-system}）而不是某个可运行应用模块</b>：这段初始化是
 * 所有装了本模块的应用都要做的事，而可运行应用模块（各种 boot 工程）各自独立、互不同步——
 * 放在那里等于每个工程各存一份副本，副本之间迟早漂移（已经出现过退化到「配置为空就直接
 * 写入空口令」的一版）。本模块是它们的公共依赖，住在这里才能一处维护。
 * <p>
 * <b>口令来源</b>（按优先级）：
 * <ol>
 *   <li>环境变量 {@code JFFAST_INIT_PASSWORD} / 配置 {@code jffast.system.init-password}；</li>
 *   <li>未配置时随机生成强口令并打印到启动日志（见下）；</li>
 *   <li>配置等于 {@code jffast.system.public-test-password}（各栈 yml 里的开箱即用示例值，
 *       随仓库公开）时，只写库并打 WARN 提醒换掉，<b>不</b>拒绝启动。</li>
 * </ol>
 *
 * @author jffast
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** seed 脚本里 admin 密码的占位值；只在这个值上动手，天然保证只初始化一次 */
    private static final String PLACEHOLDER_PASSWORD = "admin";

    private final SysUserService sysUserService;
    private final JfFastSystemProperties systemProperties;

    @Override
    public void run(String... args) {
        SysUser admin = sysUserService.lambdaQuery().eq(SysUser::getUserName, "admin").one();
        if (admin == null || !PLACEHOLDER_PASSWORD.equals(admin.getPassword())) {
            return;
        }

        String initPassword = systemProperties.getInitPassword();
        boolean generated = StrUtil.isBlank(initPassword);
        if (generated) {
            initPassword = PasswordGenerator.generate();
        }
        // publicTestPassword 本身可能被显式置空（表示「没有公开示例值」），那时不做该判断
        String publicTest = systemProperties.getPublicTestPassword();
        boolean publicTestValue = StrUtil.isNotBlank(publicTest) && publicTest.equals(initPassword);

        admin.setPassword(EncryptionUtil.getEncryptPassword(initPassword));
        sysUserService.updateById(admin);

        if (generated) {
            log.warn("================================================================");
            log.warn("未配置 jffast.system.init-password，已为 admin 随机生成初始密码：{}", initPassword);
            log.warn("请立即使用该密码登录并修改；该密码不会再次显示。");
            log.warn("如需固定初始密码，请通过环境变量 JFFAST_INIT_PASSWORD 显式指定。");
            log.warn("================================================================");
        } else if (publicTestValue) {
            log.warn("================================================================");
            log.warn("admin 初始密码为公开测试值 {}（来自 application.yml 的 init-password 默认值）。", publicTest);
            log.warn("仅限本地体验；生产环境必须通过 JFFAST_INIT_PASSWORD 注入强口令，");
            log.warn("并请登录后立即修改。");
            log.warn("================================================================");
        } else {
            log.info("管理员密码已初始化");
        }
    }
}
