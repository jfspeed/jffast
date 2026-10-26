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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * FlowableAutoConfiguration 自动装配测试。
 * <p>
 * 用 ApplicationContextRunner（轻量 Spring 上下文）验证：
 * - enabled=true 时 Bean 正确注册
 * - enabled=false 时 Bean 不注册
 * - AssigneeResolver 可选注入（无实现时不影响 Bean 创建）
 * - 业务模块提供 AssigneeResolver 时被正确注入
 *
 * @author jffast
 */
class FlowableAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlowableAutoConfiguration.class));

    @Nested
    @DisplayName("Bean 注册验证")
    class BeanRegistration {

        @Test
        @DisplayName("enabled=true + Flowable 服务存在时，应注册 FlowService 和 AssigneeTaskListener")
        void shouldRegisterBeansWhenEnabled() {
            runner
                    .withUserConfiguration(FlowableServicesMockConfig.class)
                    .withPropertyValues("jffast.flow.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(FlowService.class);
                        assertThat(context).hasSingleBean(AssigneeTaskListener.class);
                    });
        }

        @Test
        @DisplayName("enabled=false 时不应注册任何 Bean")
        void shouldNotRegisterBeansWhenDisabled() {
            runner
                    .withUserConfiguration(FlowableServicesMockConfig.class)
                    .withPropertyValues("jffast.flow.enabled=false")
                    .run(context -> {
                        assertThat(context).doesNotHaveBean(FlowService.class);
                        assertThat(context).doesNotHaveBean(AssigneeTaskListener.class);
                    });
        }

        @Test
        @DisplayName("未提供 AssigneeResolver 时 AssigneeTaskListener 仍应注册（resolver=null）")
        void shouldRegisterListenerWithoutResolver() {
            runner
                    .withUserConfiguration(FlowableServicesMockConfig.class)
                    .withPropertyValues("jffast.flow.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(AssigneeTaskListener.class);
                        assertThat(context).doesNotHaveBean(AssigneeResolver.class);
                    });
        }

        @Test
        @DisplayName("提供 AssigneeResolver 时应注入到 AssigneeTaskListener")
        void shouldInjectResolverWhenProvided() {
            runner
                    .withUserConfiguration(FlowableServicesMockConfig.class, AssigneeResolverConfig.class)
                    .withPropertyValues("jffast.flow.enabled=true")
                    .run(context -> {
                        assertThat(context).hasSingleBean(AssigneeResolver.class);
                        assertThat(context).hasSingleBean(AssigneeTaskListener.class);
                    });
        }
    }

    /**
     * 提供 Flowable 四大服务的 Mock，避免引入完整 Flowable 引擎。
     * 真实场景下这些 Bean 由 flowable-spring-boot-starter-process 自动注册。
     */
    @Configuration
    static class FlowableServicesMockConfig {
        @Bean
        RepositoryService repositoryService() {
            return mock(RepositoryService.class);
        }

        @Bean
        RuntimeService runtimeService() {
            return mock(RuntimeService.class);
        }

        @Bean
        TaskService taskService() {
            return mock(TaskService.class);
        }

        @Bean
        HistoryService historyService() {
            return mock(HistoryService.class);
        }

        @Bean
        IdentityService identityService() {
            return mock(IdentityService.class);
        }
    }

    /**
     * 提供一个测试用 AssigneeResolver Bean。
     */
    @Configuration
    static class AssigneeResolverConfig {
        @Bean
        AssigneeResolver assigneeResolver() {
            return (type, value) -> java.util.Collections.emptyList();
        }
    }
}
