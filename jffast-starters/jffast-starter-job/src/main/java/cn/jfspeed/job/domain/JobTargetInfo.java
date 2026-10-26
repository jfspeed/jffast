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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 本服务通过 {@code @JfFastJob} 声明的"可被调度的调用目标"。
 *
 * <p>既用于前端「定时任务 → 新增/编辑」弹窗的下拉（`GET /sys/job/targets`），
 * 也用于启动时把任务登记进 `sys_job`（见 {@code JobScheduler}）。
 *
 * @author jffast
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobTargetInfo {

    /** 调用目标：`beanName.methodName` 或 `beanName.methodName({param})` */
    private String invokeTarget;

    /** 任务名称（注解 {@code @JfFastJob(name=...)}） */
    private String name;

    /** 参数名列表（注解 {@code params}），无参为空列表 */
    private List<String> paramNames;

    /** Bean 名 */
    private String beanName;

    /** 方法名 */
    private String methodName;

    /** 注解声明的默认 CRON（可为空 = 未排期） */
    private String cron;

    /** 注解声明的初始启用状态（默认 false = 暂停） */
    private boolean enabled;
}
