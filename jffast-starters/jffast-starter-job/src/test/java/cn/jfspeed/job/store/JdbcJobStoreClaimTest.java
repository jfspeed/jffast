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
package cn.jfspeed.job.store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 认领的**结果语义**：{@code claim} 返回"这一行归不归我"。
 *
 * <p>认领是有竞争的：{@code UPDATE ... WHERE service_name IS NULL} 在并发下只有一个赢家，
 * 靠的就是**影响行数**（1 = 抢到了，0 = 已被别人抢走）。
 * 调用方（{@code JobScheduler}）必须据此决定排不排期 —— 认领失败还照排期，
 * 同一个任务就会被两个服务各排一次 ⇒ 执行两次。
 *
 * @author jffast
 */
class JdbcJobStoreClaimTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final JdbcJobStore store = new JdbcJobStore(jdbcTemplate);

    @Test
    @DisplayName("影响行数 1（抢到了）⇒ 认领成功")
    void claimSucceedsWhenRowUpdated() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        assertTrue(store.claim(1L, "app-a"));
    }

    @Test
    @DisplayName("影响行数 0（已被别人认领）⇒ 认领失败")
    void claimFailsWhenRowAlreadyTaken() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

        assertFalse(store.claim(1L, "app-a"));
    }

    @Test
    @DisplayName("认领过程中数据库报错 ⇒ 认领失败（不得当成成功）")
    void claimFailsOnDatabaseError() throws Exception {
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenThrow(new BadSqlGrammarException("claim", "UPDATE sys_job SET service_name = ?",
                        new SQLException("syntax error")));

        assertFalse(store.claim(1L, "app-a"));
        // 结构性错误后存储进入永久停用态，后续认领直接失败而不是抛异常
        assertFalse(store.claim(1L, "app-a"));
    }
}
