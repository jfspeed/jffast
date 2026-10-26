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
 * <b>口令来源</b>（按优先级）：
 * <ol>
 *   <li>环境变量 {@code JFFAST_INIT_PASSWORD} / 配置 {@code jffast.system.init-password}；</li>
 *   <li>未配置时用默认值 {@code test1234}（全项目统一的公开测试填充值），
 *       目标是新人 clone 后零配置登录；prod profile 已把它覆盖为必填（fail-fast）。</li>
 *   <li>显式留空 ⇒ 随机生成强口令并打印到启动日志（不想用任何固定口令时的选项）。</li>
 * </ol>
 *
 * @author jffast
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** 全项目统一的公开测试填充值（application.yml 默认值）；使用它初始化时必须打 WARN */
    private static final String PUBLIC_TEST_PASSWORD = "test1234";

    private final SysUserService sysUserService;
    private final JfFastSystemProperties systemProperties;

    @Override
    public void run(String... args) {
        SysUser admin = sysUserService.lambdaQuery().eq(SysUser::getUserName, "admin").one();
        if (admin == null || !"admin".equals(admin.getPassword())) {
            return;
        }

        String initPassword = systemProperties.getInitPassword();
        boolean generated = StrUtil.isBlank(initPassword);
        if (generated) {
            initPassword = PasswordGenerator.generate();
        }
        boolean publicTestValue = PUBLIC_TEST_PASSWORD.equals(initPassword);

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
            log.warn("admin 初始密码为公开测试值 {}（来自 application.yml 默认配置）。", PUBLIC_TEST_PASSWORD);
            log.warn("仅限本地体验；生产环境必须通过 JFFAST_INIT_PASSWORD 注入强口令，");
            log.warn("prod profile 下该值必填（缺失启动失败），并请登录后立即修改。");
            log.warn("================================================================");
        } else {
            log.info("管理员密码已初始化");
        }
    }
}
