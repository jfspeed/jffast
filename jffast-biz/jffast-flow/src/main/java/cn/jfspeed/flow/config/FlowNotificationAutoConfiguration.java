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
package cn.jfspeed.flow.config;

import java.util.ArrayList;
import java.util.List;

import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import cn.jfspeed.flow.listener.FlowNotificationEventListener;
import cn.jfspeed.flowable.config.FlowableAutoConfiguration;
import cn.jfspeed.flowable.service.FlowService;
import cn.jfspeed.system.service.SysNotificationService;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程通知自动配置。
 * <p>
 * 把 {@link FlowNotificationEventListener} 挂到 Flowable 流程引擎的全局事件监听器上，
 * 使「待办产生 / 节点办结 / 流程办结」自动写入站内通知。
 * <p>
 * 开关：{@code jffast.flow.notification.enabled}（默认 true）。
 * 系统通知本身由 {@code jffast.system.notification.enabled} 控制；后者关闭时监听器仍注册但不产生通知。
 *
 * @author jffast
 */
@Slf4j
@AutoConfiguration(after = FlowableAutoConfiguration.class)
@ConditionalOnClass({SpringProcessEngineConfiguration.class, ProcessEngineConfigurationConfigurer.class})
@ConditionalOnProperty(prefix = "jffast.flow.notification", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowNotificationAutoConfiguration {

    /**
     * 注意方法第一个参数上的 {@link Lazy}：**不要删**，它用来打断一条必然会形成的循环依赖——
     * <pre>
     * flowService → RepositoryService → ProcessEngine
     *              → 所有 ProcessEngineConfigurationConfigurer → flowNotificationEngineConfigurer
     *              → flowNotificationEventListener → flowService（回到起点）
     * </pre>
     * Spring Boot 2.6+ 默认禁止循环引用（{@code spring.main.allow-circular-references=false}），
     * 因此不加 {@code @Lazy} 会直接启动失败：{@code Requested bean is currently in creation:
     * Is there an unresolvable circular reference?}（2026-09-21 运行时验收实测）。
     * <p>
     * 加 {@code @Lazy} 是安全的：本监听器只把 flowService 保存下来，真正的调用发生在
     * Flowable 事件派发时（那时容器早已刷新完毕），拿到的是已完全初始化的真实 Bean。
     * 相比打开 {@code allow-circular-references=true} 全局开关，这样只放行这一处、不掩盖其它环。
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowNotificationEventListener flowNotificationEventListener(
            @Lazy FlowService flowService, ObjectProvider<SysNotificationService> notificationServiceProvider) {
        SysNotificationService notificationService = notificationServiceProvider.getIfAvailable();
        if (notificationService == null) {
            log.info("系统通知未启用（jffast.system.notification.enabled != true），流程通知监听器注册为空实现");
        } else {
            log.info("流程通知监听器已注册：TASK_CREATED/TASK_ASSIGNED → 待办通知，TASK_COMPLETED → 审批进展，PROCESS_COMPLETED → 办结通知");
        }
        return new FlowNotificationEventListener(flowService, notificationService);
    }

    /**
     * 把流程通知监听器追加到引擎既有的全局事件监听器列表（不覆盖 Flowable 自带监听器）。
     */
    @Bean
    public ProcessEngineConfigurationConfigurer flowNotificationEngineConfigurer(FlowNotificationEventListener listener) {
        return configuration -> {
            List<FlowableEventListener> listeners = configuration.getEventListeners() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(configuration.getEventListeners());
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
            configuration.setEventListeners(listeners);
        };
    }
}
