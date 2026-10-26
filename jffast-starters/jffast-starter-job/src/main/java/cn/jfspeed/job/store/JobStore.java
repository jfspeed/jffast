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

import java.util.List;

/**
 * 定时任务的持久化入口（`sys_job` / `sys_job_log`）。
 *
 * <p>刻意做成接口：默认实现 {@link JdbcJobStore} 用 JdbcTemplate 直连两张表，
 * 因此 **Starter 不需要依赖 `jffast-system`**；将来若要换成 MyBatis-Plus 或别的存储，
 * 只要提供一个同接口实现并标 {@code @Primary} 即可。
 *
 * @author jffast
 */
public interface JobStore {

    /** 存储是否可用（无 DataSource、或表/列缺失时为 false —— 此时定时任务静默降级，不影响服务启动） */
    boolean isAvailable();

    /** 全部任务（`sys_job` 全表；调度与归属判定都在内存里做，表通常只有几十行） */
    List<JobRecord> listAll();

    /** 按调用目标查任务（用于注解登记：判断"这个 Bean 方法是否已登记过"） */
    JobRecord findByInvokeTarget(String invokeTarget);

    /** 插入一行任务（id 由实现生成；`create_time` 等交给数据库默认值） */
    void insert(JobRecord record);

    /**
     * 认领：把 `service_name` 为空的任务归属到指定服务。
     * <p>用于老数据平滑迁移 —— 某个服务的代码里存在该任务的 Bean，就由它接手执行。
     */
    void claim(Long id, String serviceName);

    /** 写一条执行日志 */
    void insertLog(JobLogRecord record);
}
