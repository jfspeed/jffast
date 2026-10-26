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
package cn.jfspeed.job.handler;

import org.quartz.DisallowConcurrentExecution;

/**
 * 不并发执行的执行体 —— 对应 `sys_job.concurrent = 0`（交付默认）。
 *
 * <p><b>为什么需要单独一个类</b>：Quartz 的并发控制是 **Job 类级注解**
 * （{@link DisallowConcurrentExecution} 的语义按 `JobKey` 生效，跟"行"无关），
 * 所以"某条任务是否允许并发"只能通过**挑不同的 Job 类**来表达。
 * 改造前 `sys_job.concurrent` 字段一直是**死配置**（`QuartzManager` 只把它塞进 JobDataMap，没人读）。
 *
 * <p>行为说明：同类任务在前一次执行未结束时，本次触发会被 Quartz **排队等待**
 * （不是丢弃），跨副本的"同一时刻只跑一次"仍由 {@code JobLock}（Redis）负责。
 *
 * @author jffast
 */
@DisallowConcurrentExecution
public class DisallowConcurrentQuartzJob extends DefaultQuartzJob {
}
