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

    /**
     * 全部任务（`sys_job` 全表；调度与归属判定都在内存里做，表通常只有几十行）。
     *
     * <p><b>读不到时返回 {@code null}</b>（存储不可用 / 瞬时故障进冷却 / 缺表），
     * 与"表里确实没有任务"的**空列表**严格区分：对账逻辑会按"表里没有"清理触发器，
     * 若把读取失败也报成空表，一次数据库抖动就会清空本进程的全部触发器。
     */
    List<JobRecord> listAll();

    /** 按调用目标查任务（用于注解登记：判断"这个 Bean 方法是否已登记过"） */
    JobRecord findByInvokeTarget(String invokeTarget);

    /**
     * 插入一行任务（id 由实现生成；`create_time` 等交给数据库默认值）。
     *
     * <p><b>为什么必须返回结果</b>：插入可能**没有真的发生**——存储不可用（冷却期/缺表）、
     * 并发登记撞唯一键、写入异常。三种情况在调用方看来都是"调用了 insert"，若不回传结果，
     * 登记日志会一律打印「登记新任务」，而任务表里其实**没有这一行**：页面看不到任务、
     * 日志却显示已登记，属于典型的自欺故障。
     *
     * @return true = 真的插入了这一行；false = 没有插入（已存在 / 存储不可用 / 写入失败）
     */
    boolean insert(JobRecord record);

    /**
     * 认领：把 `service_name` 为空的任务归属到指定服务。
     * <p>用于老数据平滑迁移 —— 某个服务的代码里存在该任务的 Bean，就由它接手执行。
     *
     * <p><b>为什么必须返回结果</b>：认领是**有竞争**的 —— 多个服务可能同时认领同一行，
     * 库里靠 {@code AND service_name IS NULL} 保证只有一个赢家。
     * 调用方必须据此决定"这一行归不归我"：
     * 认领失败还照排期 ⇒ 同一个任务被两个服务各排一次 ⇒ <b>被执行两次</b>。
     *
     * @return true = 认领成功（该行此刻归 {@code serviceName}）；
     *         false = 认领失败（该行已被别的服务认领 / 行已不存在 / 存储不可用）
     */
    boolean claim(Long id, String serviceName);

    /** 写一条执行日志 */
    void insertLog(JobLogRecord record);
}
