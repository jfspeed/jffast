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
package cn.jfspeed.demomode.runner;

import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * 演示模式启动时暂停 Quartz 全量调度（定时任务不实际执行）。
 * <p>
 * 任务定义 CRUD 照常（走 DB 写短路，返回成功不入库）；手动触发由
 * {@code DemoModeSideEffectAspect} 拦截并明示。仅演示 profile 装配本类。
 *
 * @author jffast
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class DemoModeSchedulerPauser implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoModeSchedulerPauser.class);

    private final ObjectProvider<Scheduler> schedulerProvider;

    public DemoModeSchedulerPauser(ObjectProvider<Scheduler> schedulerProvider) {
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        Scheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler == null) {
            log.info("[DemoMode] 无 Quartz Scheduler，跳过暂停");
            return;
        }
        try {
            scheduler.pauseAll();
            log.info("[DemoMode] Quartz 调度已全部暂停（演示模式）");
        } catch (Exception e) {
            log.warn("[DemoMode] 暂停 Quartz 调度失败", e);
        }
    }
}
