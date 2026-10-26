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
import cn.jfspeed.job.domain.JobRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * 无数据库（无 {@code JdbcTemplate}）时的空实现：`isAvailable()` 恒为 false，
 * 调度器据此**安静地跳过**登记与调度 —— 让 Starter 可以安全地出现在任何服务里。
 *
 * @author jffast
 */
@Slf4j
public class NoopJobStore implements JobStore {

    public NoopJobStore() {
        log.info("【定时任务】未检测到数据库（JdbcTemplate），定时任务登记与调度已跳过");
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public List<JobRecord> listAll() {
        return Collections.emptyList();
    }

    @Override
    public JobRecord findByInvokeTarget(String invokeTarget) {
        return null;
    }

    @Override
    public void insert(JobRecord record) {
        // no-op
    }

    @Override
    public boolean claim(Long id, String serviceName) {
        // no-op：无存储可写 ⇒ 认领不成立
        return false;
    }

    @Override
    public void insertLog(JobLogRecord record) {
        // no-op
    }
}
