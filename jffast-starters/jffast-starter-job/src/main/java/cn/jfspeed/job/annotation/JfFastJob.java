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
package cn.jfspeed.job.annotation;

import java.lang.annotation.*;

/**
 * 声明一个定时任务（**任务定义以注解为主**，数据库 `sys_job` 只承载"调度参数"）。
 *
 * <p>标注在 Spring Bean 的方法上即可：该方法所属的 **服务**（`spring.application.name`）就是
 * 这个任务的**归属服务** —— 启动时由 {@code JobScheduler} 自动把任务登记进 `sys_job`
 * （`service_name` = 本服务名），**只有归属服务会调度它**，其它服务扫到同名的也不会执行。
 *
 * <pre>
 * &#64;Service("demoJobService")
 * public class DemoJobService {
 *     // 只声明"我是任务"，cron 与启停由管理员在「系统管理 → 定时任务」里配
 *     &#64;JfFastJob(name = "清理过期数据")
 *     public void cleanExpiredData() { ... }
 *
 *     // 也可以带上默认 cron；enabled=false（默认）⇒ 入库为暂停状态，需管理员显式启用
 *     &#64;JfFastJob(name = "生成日报", cron = "0 0 6 * * ?", params = "reportType", enabled = true)
 *     public void generateReport(String reportType) { ... }
 * }
 * </pre>
 *
 * @author jffast
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface JfFastJob {

    /** 任务名称（展示名，同时作为 `sys_job.job_name`） */
    String name();

    /** 参数名（逗号分隔）。有参数时调用目标形如 `bean.method({p1})`，运行时整体作为 1 个 String 传入 */
    String params() default "";

    /**
     * 默认 CRON 表达式；留空表示"尚未排期" —— 任务会被登记进列表但**不会执行**，
     * 由管理员在页面上填写 CRON 后启用。
     */
    String cron() default "";

    /**
     * 登记时的初始启用状态，默认 {@code false}（暂停）。
     * <p>**刻意默认暂停**：任务的启用属于运维决策（要评估频率、影响面），
     * 不允许"加个注解就上线"。已存在的任务不会被注解覆盖 —— 管理员改过的 cron/启停永远优先。
     */
    boolean enabled() default false;
}
