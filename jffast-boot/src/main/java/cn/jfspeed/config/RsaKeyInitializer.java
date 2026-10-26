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

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.config.JfFastProperties;
import cn.jfspeed.common.security.util.RSAUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 非生产环境 RSA 密钥对自动装配。
 * <p>
 * 背景：仓库曾经内置一套「示例密钥对」用于开箱即用，但私钥一旦入库即永久泄露
 * （git 历史不可逆），而该私钥的用途正是解密登录/改密请求中的密码明文。
 * 因此这里改为：密钥不入库，未配置时由本类在启动时生成。
 * <p>
 * 行为：
 * <ul>
 *   <li><b>prod</b>：未显式注入密钥 ⇒ 启动直接失败（fail-fast）。
 *       生产环境多实例部署时，各实例密钥必须一致，否则登录请求打到不同实例会解密失败。</li>
 *   <li><b>dev / test</b>：未配置 ⇒ 生成一次性密钥对并打印 WARN。密钥仅存在于当前进程内存，
 *       前端通过 {@code GET /sys/rsaPublicKey} 动态取公钥，因此无需手工同步。</li>
 * </ul>
 * <p>
 * 需要固定密钥时（多实例、灰度、prod），用 {@code cn.jfspeed.common.security.util.RsaKeyGenerator}
 * 生成后通过环境变量注入：
 * <pre>
 *   RSA_PRIVATE_KEY=&lt;私钥&gt;  RSA_PUBLIC_KEY=&lt;公钥&gt;
 * </pre>
 *
 * @author jffast
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RsaKeyInitializer implements ApplicationRunner {

    private final JfFastProperties rsaProperties;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        boolean privateKeyMissing = StrUtil.isBlank(rsaProperties.getPrivateKey());
        boolean publicKeyMissing = StrUtil.isBlank(rsaProperties.getPublicKey());
        if (!privateKeyMissing && !publicKeyMissing) {
            return;
        }

        if (isProduction()) {
            throw new IllegalStateException(
                    "生产环境必须显式配置 RSA 密钥对：请通过环境变量 RSA_PRIVATE_KEY / RSA_PUBLIC_KEY 注入"
                            + "（多实例部署时各实例必须一致，否则登录解密会失败）。"
                            + "可用 cn.jfspeed.common.security.util.RsaKeyGenerator 生成。");
        }

        List<String> pair = RSAUtil.generateKey();
        rsaProperties.setPublicKey(pair.get(0));
        rsaProperties.setPrivateKey(pair.get(1));

        // 只提示「已自动生成」，绝不打印密钥内容：日志一旦被收集即等同于私钥泄露
        log.warn("================================================================");
        log.warn("未检测到 RSA 密钥配置，已自动生成一次性密钥对（仅存在于当前进程内存，重启即失效）。");
        log.warn("当前为 {} 环境，可用于本地开发联调。", String.join(",", environment.getActiveProfiles()));
        log.warn("多实例部署或生产环境请显式注入 RSA_PRIVATE_KEY / RSA_PUBLIC_KEY，"
                + "否则各实例密钥不一致会导致登录解密失败。");
        log.warn("================================================================");
    }

    /** 是否为生产环境（prod profile） */
    private boolean isProduction() {
        return environment.matchesProfiles("prod");
    }
}
