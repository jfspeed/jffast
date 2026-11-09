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
package cn.jfspeed.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归：{@link JfFastFileAutoConfiguration} 必须真实注册
 * {@link FileStorageProperties}（改造曾把 {@code @EnableConfigurationProperties} 弄丢，
 * 单测全绿但应用起不来——纯直调 mock 的用例验证不了真实装配链）。
 *
 * @author jffast
 */
class FileStoragePropertiesRegistrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JfFastFileAutoConfiguration.class))
            // runner 无 DataSource：登记器必然不可用，required=false 让 localFileStorageService
            // 走显式降级分支，从而隔离验证「properties 注册」本身（fail-closed 由
            // FileRecordAutoConfigurationTest 的直调用例覆盖）。
            .withPropertyValues("jffast.file.record.required=false");

    @Test
    @DisplayName("真实装配链下 FileStorageProperties 必须是可用 bean（回归：@EnableConfigurationProperties 曾被弄丢）")
    void fileStoragePropertiesBeanMustExist() {
        runner.withPropertyValues("jffast.file.sign.secret=ctx-test-secret").run(context -> {
            assertThat(context).hasSingleBean(FileStorageProperties.class);
            assertThat(context.getBean(FileStorageProperties.class).getLocal().getPath()).isEqualTo("upload");
        });
    }

    @Test
    @DisplayName("非 dev/test profile 且未配密钥：真实装配链下启动失败（fail-closed 保持）")
    void missingSecretFailsFastInProductionLikeContext() {
        runner.run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }

    @Test
    @DisplayName("dev profile 未配密钥：真实装配链下正常启动（本地开发口径）")
    void devProfileAllowsMissingSecret() {
        runner.withPropertyValues("spring.profiles.active=dev").run(context ->
                assertThat(context).hasSingleBean(FileStorageProperties.class));
    }
}
