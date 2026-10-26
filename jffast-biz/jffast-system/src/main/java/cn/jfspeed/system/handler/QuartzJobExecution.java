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
package cn.jfspeed.system.handler;

import cn.jfspeed.job.handler.DefaultQuartzJob;

/**
 * 任务执行体（**兼容别名**）。
 *
 * <p>执行体已下沉到 `jffast-starter-job` 的 {@link DefaultQuartzJob}
 * （带 Redis 分布式锁 + 自带 `sys_job_log` 落库 + 归属服务语义）。
 *
 * <p><b>为什么这个类不能删</b>（单体实测教训）：单体形态用的是 Quartz **JDBC jobstore**
 * （`QRTZ_*` 表，见 `jffast-boot/application.yml` 的 `job-store-type: jdbc`），旧触发器会把
 * **执行类的全限定名持久化在数据库里**。删掉本类后，调度器一旦 `rescheduleJob` / 删除这些旧触发器，
 * Quartz 就要加载该类 → `JobPersistenceException: Couldn't remove trigger:
 * cn.jfspeed.system.handler.QuartzJobExecution [Caused by: ClassNotFoundException]`，
 * 表现为"任务再也改不动 cron、也执行不了"，且**只有存量部署（有历史 QRTZ_ 数据）才会踩到**。
 *
 * <p>保留成本几乎为零：它只是替身，行为完全等同于新执行体 ——
 * 历史触发器照旧能加载，并且**自动获得新行为**（加锁、日志落库到 starter 侧）。
 *
 * @author jffast
 * @deprecated 新代码请直接使用 {@code cn.jfspeed.job.handler.DefaultQuartzJob}；
 *             本类仅为兼容存量 JDBC jobstore 的历史触发器而保留，不要在其中添加逻辑。
 */
@Deprecated(since = "2026-09-23")
public class QuartzJobExecution extends DefaultQuartzJob {
}
