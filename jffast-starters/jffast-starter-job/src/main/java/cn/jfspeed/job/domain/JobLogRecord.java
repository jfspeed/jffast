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
package cn.jfspeed.job.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * `sys_job_log` 一行（Starter 视角的轻量模型，由 {@code JobStore#insertLog} 落库）。
 *
 * @author jffast
 */
@Data
public class JobLogRecord {

    /**
     * 执行线程的租户快照；{@code null} = 平台级（与 {@code sys_job.tenant_id} 的 NULL 语义一致）。
     * <p>不写会让租户任务的日志落 NULL：读侧被追加 {@code tenant_id = <本租户>}，
     * 租户在自己的任务日志页反而看不到自己任务的执行记录。
     */
    private Long tenantId;

    private String jobName;

    private String jobGroup;

    private String invokeTarget;

    /** 执行状态（1成功 2失败） */
    private Integer status;

    private String errorMsg;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 耗时（毫秒） */
    private Long duration;
}
