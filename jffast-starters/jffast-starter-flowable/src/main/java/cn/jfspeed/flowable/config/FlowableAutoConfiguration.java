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
package cn.jfspeed.flowable.config;

import cn.jfspeed.flowable.listener.AssigneeTaskListener;
import cn.jfspeed.flowable.resolver.AssigneeResolver;
import cn.jfspeed.flowable.service.FlowService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Flowable 工作流引擎自动配置。
 * <p>
 * 仅在 classpath 存在 Flowable 引擎类且 {@code jffast.flow.enabled=true}（默认开启）时生效。
 * 引擎本身的配置（数据源/事务管理器/schema 更新/历史级别）由 Flowable 官方
 * {@code FlowableAutoConfiguration} 完成，本类只补充 jffast 自定义 Bean。
 *
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(SpringProcessEngineConfiguration.class)
@EnableConfigurationProperties(FlowableProperties.class)
@ConditionalOnProperty(prefix = "jffast.flow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowableAutoConfiguration {

    /**
     * 审批人解析监听器：BPMN UserTask 通过 delegateExpression="${assigneeTaskListener}" 引用，
     * 任务创建时优先按任务级 BPMN 扩展属性 flowable:assigneeType/assigneeValue（设计器画布产出）
     * 解析实际审批人，其次回退流程变量 assigneeType/assigneeValue（业务层发起时写入）。
     * <p>
     * AssigneeResolver 可选：业务模块（jffast-flow）提供实现后支持 role/dept 解析，
     * 未提供则只支持 user/initiator。
     */
    @Bean
    @ConditionalOnMissingBean
    public AssigneeTaskListener assigneeTaskListener(RuntimeService runtimeService,
                                                      TaskService taskService,
                                                      RepositoryService repositoryService,
                                                      ObjectProvider<AssigneeResolver> resolverProvider) {
        return new AssigneeTaskListener(runtimeService, taskService, repositoryService, resolverProvider.getIfAvailable());
    }

    /**
     * 工作流服务门面：封装 Flowable 的 Repository/Runtime/Task/History/Identity 服务，
     * 业务模块注入此 Bean 即可完成部署/发起/审批/历史查询，无需直接接触各 Flowable 服务。
     */
    @Bean
    @ConditionalOnMissingBean
    public FlowService flowService(RepositoryService repositoryService,
                                   RuntimeService runtimeService,
                                   TaskService taskService,
                                   HistoryService historyService,
                                   IdentityService identityService) {
        return new FlowService(repositoryService, runtimeService, taskService, historyService, identityService);
    }
}


