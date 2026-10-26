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

/**
 * `sys_job` 一行（Starter 视角的轻量模型）。
 *
 * <p>Starter **不依赖 `jffast-system`**（那是业务模块），因此这里自带一个与表一一对应的 POJO，
 * 由 {@code JdbcJobStore} 用 JdbcTemplate 读写 —— 这样任何服务只要引入本 Starter 就拥有
 * "登记 / 调度自己任务"的能力，无需把整个 system 模块编进 classpath。
 *
 * @author jffast
 */
@Data
public class JobRecord {

    private Long id;

    /** 任务名称（展示名，唯一键之一：job_name + job_group） */
    private String jobName;

    /** 任务组（默认 DEFAULT；同一组内任务名唯一） */
    private String jobGroup;

    /** 调用目标：`beanName.methodName` 或 `beanName.methodName(param)` */
    private String invokeTarget;

    /** CRON 表达式；空 = 尚未排期（登记但不执行） */
    private String cronExpression;

    /** 计划执行策略（1立即执行 2放弃执行 3下次执行），沿用既有取值 */
    private String misfirePolicy;

    /** 是否并发执行（0否 1是），沿用既有取值 */
    private Integer concurrent;

    /** 状态（0暂停 1正常） */
    private Integer status;

    /**
     * **执行该任务的服务名**（`spring.application.name`）。
     * <p>NULL = 无归属（老数据 / 页面手工新建）⇒ 由 {@code jffast.job.scheduler.default-scope=true} 的服务执行。
     */
    private String serviceName;

    private String remark;

    /** 是否启用（便捷方法） */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
