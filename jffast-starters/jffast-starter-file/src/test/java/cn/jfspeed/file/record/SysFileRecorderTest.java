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
package cn.jfspeed.file.record;

import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.file.config.FileStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code sys_file} 上传登记用例（AC-03）。
 *
 * <p>登记是 fail-closed 的：没有 JdbcTemplate、SQL 失败，都必须抛业务异常，
 * 而不是「上传成功但没有归属记录」——后者会留下既不可见也不可管的孤儿文件。
 *
 * <p>租户口径：登记必须写 {@code tenant_id}，来源优先租户上下文桥，退回登录用户租户，
 * 皆空落 0（平台/单租户）。
 *
 * @author jffast
 */
class SysFileRecorderTest {

    private static final String URL = "/upload/2026/11/a.png";

    private static FileStorageProperties properties() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setType("local");
        return properties;
    }

    @Test
    @DisplayName("登记成功：返回雪花 ID，且 INSERT 带 tenant_id")
    void recordReturnsIdAndWritesTenantColumn() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        SysFileRecorder recorder = new SysFileRecorder(jdbc, properties());
        Long id = recorder.record("user-avatar", "a.png", URL, 12L, "image/png");

        assertNotNull(id);
        assertTrue(id > 0);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertTrue(sql.getValue().contains("tenant_id"),
                "INSERT 必须显式带 tenant_id：" + sql.getValue());
    }

    @Test
    @DisplayName("登记的 tenant_id 是最后一个参数：值来自租户上下文桥")
    void recordUsesTenantScopeBridgeValue() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TenantScopeBridge bridge = mock(TenantScopeBridge.class);
        when(bridge.currentTenantId()).thenReturn(42L);

        SysFileRecorder recorder = new SysFileRecorder(jdbc, properties(), providerOf(bridge));
        recorder.record("user-avatar", "a.png", URL, 12L, "image/png");

        ArgumentCaptor<Object> last = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), last.capture());
        assertEquals(42L, last.getValue(), "最后一个参数应是 tenant_id");
    }

    @Test
    @DisplayName("没有 JdbcTemplate：登记失败（fail-closed），不得静默成功")
    void missingJdbcTemplateFailsClosed() {
        SysFileRecorder recorder = new SysFileRecorder(null, properties());

        assertFalse(recorder.isAvailable());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> recorder.record("user-avatar", "a.png", URL, 12L, "image/png"));
        assertEquals("上传登记能力未启用：缺少 JdbcTemplate", ex.getMessage());
    }

    @Test
    @DisplayName("INSERT 失败：抛业务异常（不返回 ID）")
    void sqlFailureThrows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new RuntimeException("table missing")).when(jdbc)
                .update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        SysFileRecorder recorder = new SysFileRecorder(jdbc, properties());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> recorder.record("user-avatar", "a.png", URL, 12L, "image/png"));
        assertEquals("上传登记失败，请重试", ex.getMessage());
    }

    @Test
    @DisplayName("清理记录：按 URL 删除；没有 JdbcTemplate 时静默跳过")
    void removeByUrl() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new SysFileRecorder(jdbc, properties()).remove(URL);

        verify(jdbc).update(eq("DELETE FROM sys_file WHERE url = ?"), eq(URL));
        // 无 JdbcTemplate 时不抛异常（清理失败不影响删除主流程）
        new SysFileRecorder(null, properties()).remove(URL);
    }

    /** 构造一个总是返回给定桥的 ObjectProvider */
    @SuppressWarnings("unchecked")
    private static ObjectProvider<TenantScopeBridge> providerOf(TenantScopeBridge bridge) {
        ObjectProvider<TenantScopeBridge> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bridge);
        return provider;
    }
}
