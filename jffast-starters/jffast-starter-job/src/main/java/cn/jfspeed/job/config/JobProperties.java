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
package cn.jfspeed.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务 Starter 配置属性。
 *
 * <pre>
 * jffast:
 *   job:
 *     enabled: true                 # 整个定时任务能力的总开关（false ⇒ 不注册任何 job Bean，页面接口也不存在）
 *     scheduler:
 *       enabled: true               # 本服务是否调度任务（默认 true）
 *       default-scope: false        # 是否调度「无归属」任务（service_name IS NULL）
 *       refresh-interval-seconds: 30  # 每 N 秒同步一次 sys_job（启停/cron 改动无需重启）；0=关闭
 *     lock:
 *       ttl-seconds: 300            # 执行锁 TTL：防止实例崩溃后锁残留过久（正常执行完会立即释放）
 *     invoke:
 *       allowed-packages:           # 调用目标白名单包前缀（默认空：只允许 @JfFastJob 声明的方法）
 *         - com.example.tasks
 * </pre>
 *
 * <p><b>归属规则</b>：任务的执行者是 `sys_job.service_name`。注解登记的任务，
 * 其归属自动 = 声明它的服务名（`spring.application.name`）；**只有归属服务会调度它**。
 * 归属为空的（老数据 / 页面手工新建的）由 {@code scheduler.default-scope=true} 的服务**认领** ——
 * 认领结果写回 {@code service_name}，且认领是**有竞争**的（库里靠 {@code AND service_name IS NULL}
 * 保证只有一个赢家），**认领失败的服务不排期** ⇒ 多个服务同时打开也不会把同一个任务执行两次。
 * 单体只有一个进程，保持 true 即可。
 *
 * @author jffast
 */
@Data
@ConfigurationProperties(prefix = "jffast.job")
public class JobProperties {

    /** 是否启用定时任务模块，默认 true（false ⇒ 不装配任何 job Bean，含管理接口） */
    private boolean enabled = true;

    private Scheduler scheduler = new Scheduler();

    private Lock lock = new Lock();

    private Invoke invoke = new Invoke();

    @Data
    public static class Scheduler {

        /** 本服务是否调度任务（默认 true）。false ⇒ 本服务不调度任何任务（含自己归属的） */
        private boolean enabled = true;

        /**
         * 是否调度「无归属」任务（`service_name IS NULL`）。
         * <p>默认 false：打开它意味着本服务会接手**跑得动**的无归属任务（调用目标的 Bean
         * 在本进程 classpath 里），多服务部署时由指定的"默认调度者"显式打开即可。
         * <p>多个服务同时打开也是安全的 —— 认领有竞争（只有一个赢家），认领失败者不排期。
         */
        private boolean defaultScope = false;

        /** 同步间隔（秒），0 = 不周期同步（启停/cron 改动需重启生效） */
        private int refreshIntervalSeconds = 30;
    }

    @Data
    public static class Lock {

        /** 执行锁 TTL（秒）。任务执行完会主动释放；TTL 只用于兜底"实例崩溃"场景 */
        private long ttlSeconds = 300;
    }

    @Data
    public static class Invoke {

        /**
         * 调用目标（`invoke_target`）白名单包前缀。
         * <p>安全约束：默认只允许调用标注 {@code @JfFastJob} 的方法，防止拿到任务管理权限后
         * 通过任意 Bean 方法提权。确有非注解任务时，在此显式追加方法所在包前缀，
         * 例如 {@code com.example.tasks}。
         */
        private List<String> allowedPackages = new ArrayList<>();
    }
}
