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

import cn.jfspeed.job.domain.JobLogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务执行日志的租户归属契约测试。
 *
 * <p>背景：`sys_job_log` 建了 `tenant_id` 列，但旧实现写日志时不带该列 ⇒ 租户任务的执行记录落 NULL；
 * 读侧（MyBatis 租户拦截器）会追加 `tenant_id = <本租户>`，于是租户在自己的任务日志页看不到自己任务的日志。
 * 这里钉住两条：<b>租户任务写任务租户</b>、<b>平台级任务写 NULL（不是 0）</b>。
 *
 * @author jffast
 */
class JdbcJobStoreTenantLogTest {

    @Test
    @DisplayName("租户任务写日志：tenant_id 落任务租户，不再是 NULL")
    void writesTaskTenant() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        JdbcJobStore store = new JdbcJobStore(jdbc);

        store.insertLog(record(100L));

        assertTrue(jdbc.sql.contains("tenant_id"), "INSERT 列清单必须显式包含 tenant_id");
        assertEquals(100L, jdbc.args[1], "id 之后的第一个占位符应是任务租户");
    }

    @Test
    @DisplayName("平台级任务（无租户上下文）写 NULL，不写 0")
    void platformTaskWritesNull() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        JdbcJobStore store = new JdbcJobStore(jdbc);

        store.insertLog(record(null));

        assertNull(jdbc.args[1], "平台级语义统一为 NULL（与 sys_job.tenant_id 一致），不得写成 0");
    }

    private static JobLogRecord record(Long tenantId) {
        JobLogRecord record = new JobLogRecord();
        record.setTenantId(tenantId);
        record.setJobName("demo-job");
        record.setJobGroup("DEFAULT");
        record.setInvokeTarget("demoJobService.run()");
        record.setStatus(1);
        record.setStartTime(LocalDateTime.now());
        record.setEndTime(LocalDateTime.now());
        record.setDuration(1L);
        return record;
    }

    /** 只抓 SQL 与参数，不连数据库（本类的目的是钉住写入参数，不是测 JdbcTemplate）。 */
    private static class CapturingJdbcTemplate extends JdbcTemplate {

        private String sql;

        private Object[] args;

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 1;
        }
    }
}
