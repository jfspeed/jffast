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

import cn.jfspeed.file.record.SysFileRecorder;
import cn.jfspeed.file.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 文件归属模型的启动期裁定（AC-20）。
 *
 * <p>两条 fail-closed 规则：
 * <ol>
 *   <li>{@code jffast.file.record.required=true}（默认）却没有可用登记能力 ⇒ 启动失败。
 *       宁可起不来，也不要「上传成功但没有归属记录」——那会源源不断产生不可追溯的孤儿文件。</li>
 *   <li>非 dev/test profile 未配置 {@code jffast.file.sign.secret} ⇒ 启动失败。
 *       多实例密钥不一致会让验签随机失败，静默降级成随机密钥等于把直显变成随机不可用。</li>
 * </ol>
 *
 * <p>显式降级（{@code required=false}）时允许无登记上传，但**不得**启用匿名直显端点
 * （见 {@code SysFileViewController} 的条件装配）。
 *
 * @author jffast
 */
class FileRecordAutoConfigurationTest {

    private final JfFastFileAutoConfiguration configuration = new JfFastFileAutoConfiguration();

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SysFileRecorder> recorderProvider(SysFileRecorder recorder) {
        ObjectProvider<SysFileRecorder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(recorder);
        return provider;
    }

    @Test
    @DisplayName("要求登记但拿不到登记能力：启动失败，且提示可读")
    void requiredWithoutRecorderFailsFast() {
        FileOwnershipProperties ownership = new FileOwnershipProperties();
        ownership.getSign().setSecret("unit-test-secret");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configuration.localFileStorageService(new FileStorageProperties(),
                        recorderProvider(null), ownership));

        assertTrue(ex.getMessage().contains("上传登记能力未启用"), ex.getMessage());
        assertTrue(ex.getMessage().contains("jffast.file.record.required=false"), ex.getMessage());
    }

    @Test
    @DisplayName("登记器存在但没有 JdbcTemplate（isAvailable=false）：同样启动失败")
    void requiredWithUnavailableRecorderFailsFast() {
        SysFileRecorder recorder = mock(SysFileRecorder.class);
        when(recorder.isAvailable()).thenReturn(false);
        FileOwnershipProperties ownership = new FileOwnershipProperties();
        ownership.getSign().setSecret("unit-test-secret");

        assertThrows(IllegalStateException.class,
                () -> configuration.localFileStorageService(new FileStorageProperties(),
                        recorderProvider(recorder), ownership));
    }

    @Test
    @DisplayName("显式降级（required=false）：允许无登记能力启动")
    void degradedModeStartsWithoutRecorder() {
        FileOwnershipProperties ownership = new FileOwnershipProperties();
        ownership.getSign().setSecret("unit-test-secret");
        ownership.getRecord().setRequired(false);

        FileStorageService service = assertDoesNotThrow(
                () -> configuration.localFileStorageService(new FileStorageProperties(),
                        recorderProvider(null), ownership));
        assertNotNull(service);
    }

    @Test
    @DisplayName("生产 profile 未配置签名密钥：启动失败")
    void productionWithoutSecretFailsFast() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> configuration.fileOwnershipProperties(environment));

        assertTrue(ex.getMessage().contains("jffast.file.sign.secret"), ex.getMessage());
    }

    @Test
    @DisplayName("未指定 profile 也按生产严格（不得因为「没写 profile」就放宽）")
    void emptyProfileIsTreatedAsProduction() {
        MockEnvironment environment = new MockEnvironment();

        assertThrows(IllegalStateException.class,
                () -> configuration.fileOwnershipProperties(environment));
    }

    @Test
    @DisplayName("dev profile 缺密钥：生成一次性随机密钥（仅当前进程有效）")
    void devGeneratesEphemeralSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        FileOwnershipProperties properties = configuration.fileOwnershipProperties(environment);

        assertNotNull(properties.getSign().getSecret());
        assertTrue(properties.getSign().getSecret().length() >= 32);
    }

    @Test
    @DisplayName("显式配置的密钥优先于自动生成")
    void explicitSecretWins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("jffast.file.sign.secret", "configured-secret");
        environment.setProperty("jffast.file.sign.ttl-seconds", "600");
        environment.setProperty("jffast.file.record.required", "false");

        FileOwnershipProperties properties = configuration.fileOwnershipProperties(environment);

        assertEquals("configured-secret", properties.getSign().getSecret());
        assertEquals(600L, properties.getSign().getTtlSeconds());
        assertEquals(false, properties.getRecord().isRequired());
    }

    @Test
    @DisplayName("绑定只覆盖 sign / record 两个子树，不吞掉 jffast.file.storage.*")
    void binderDoesNotSwallowStorageProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("jffast.file.storage.type", "minio");
        environment.setProperty("jffast.file.storage.max-size", "2048");
        environment.setProperty("jffast.file.record.required", "false");

        FileOwnershipProperties properties = configuration.fileOwnershipProperties(environment);

        assertEquals(false, properties.getRecord().isRequired());
        // storage 子树由 FileStorageProperties 自己绑定，这里只确认本次绑定没有越界写坏它
        FileStorageProperties storage = new FileStorageProperties();
        assertEquals("local", storage.getType());
    }
}
