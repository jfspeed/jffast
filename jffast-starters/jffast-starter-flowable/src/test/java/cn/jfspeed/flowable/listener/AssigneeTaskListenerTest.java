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
package cn.jfspeed.flowable.listener;

import cn.jfspeed.flowable.constant.FlowConstant;
import cn.jfspeed.flowable.resolver.AssigneeResolver;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AssigneeTaskListener 单元测试。
 * <p>
 * 验证任务创建时的审批人解析逻辑：user/role/dept/initiator 四种类型，
 * 单人 → setAssignee，多人 → addCandidateUser。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssigneeTaskListenerTest {

    @Mock
    private RuntimeService runtimeService;
    @Mock
    private TaskService taskService;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private DelegateTask delegateTask;
    @Mock
    private AssigneeResolver assigneeResolver;

    private AssigneeTaskListener listener;
    private AssigneeTaskListener listenerWithoutResolver;

    @BeforeEach
    void setUp() {
        listener = new AssigneeTaskListener(runtimeService, taskService, repositoryService, assigneeResolver);
        listenerWithoutResolver = new AssigneeTaskListener(runtimeService, taskService, repositoryService, null);
        when(delegateTask.getExecutionId()).thenReturn("exec-1");
        when(delegateTask.getId()).thenReturn("task-1");
    }

    @Nested
    @DisplayName("类型=user：直接指定用户")
    class UserType {

        @Test
        @DisplayName("单个用户应 setAssignee")
        void shouldSetAssigneeForSingleUser() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("u-100");

            listener.notify(delegateTask);

            verify(taskService).setAssignee("task-1", "u-100");
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }

        @Test
        @DisplayName("多个用户应 addCandidateUser")
        void shouldAddCandidateForMultipleUsers() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("u-1,u-2,u-3");

            listener.notify(delegateTask);

            verify(taskService).addCandidateUser("task-1", "u-1");
            verify(taskService).addCandidateUser("task-1", "u-2");
            verify(taskService).addCandidateUser("task-1", "u-3");
            verify(taskService, never()).setAssignee(anyString(), anyString());
        }

        @Test
        @DisplayName("value 为空应跳过（不调用任何 TaskService 方法）")
        void shouldSkipWhenValueEmpty() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("");

            listener.notify(delegateTask);

            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }

        @Test
        @DisplayName("value 含空格应 trim")
        void shouldTrimValues() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn(" u-1 , u-2 ");

            listener.notify(delegateTask);

            verify(taskService).addCandidateUser("task-1", "u-1");
            verify(taskService).addCandidateUser("task-1", "u-2");
        }
    }

    @Nested
    @DisplayName("类型=initiator：发起人自己")
    class InitiatorType {

        @Test
        @DisplayName("应从流程变量 initiatorUserId 解析为单用户")
        void shouldResolveInitiatorFromProcessVar() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_INITIATOR);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn(null);
            when(runtimeService.getVariable("exec-1", FlowConstant.VAR_INITIATOR_USER_ID))
                    .thenReturn("u-999");

            listener.notify(delegateTask);

            verify(taskService).setAssignee("task-1", "u-999");
        }

        @Test
        @DisplayName("initiatorUserId 为 null 应跳过")
        void shouldSkipWhenInitiatorNull() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_INITIATOR);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn(null);
            when(runtimeService.getVariable("exec-1", FlowConstant.VAR_INITIATOR_USER_ID))
                    .thenReturn(null);

            listener.notify(delegateTask);

            verify(taskService, never()).setAssignee(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("类型=role/dept：需 AssigneeResolver 实现")
    class RoleDeptType {

        @Test
        @DisplayName("role 类型 + resolver 解析为单用户 → setAssignee")
        void shouldResolveRoleToSingleUser() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_ROLE);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("manager");
            when(assigneeResolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager"))
                    .thenReturn(List.of("u-1"));

            listener.notify(delegateTask);

            verify(taskService).setAssignee("task-1", "u-1");
        }

        @Test
        @DisplayName("dept 类型 + resolver 解析为多用户 → addCandidateUser")
        void shouldResolveDeptToMultipleUsers() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_DEPT);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("100,200");
            when(assigneeResolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_DEPT, "100,200"))
                    .thenReturn(List.of("u-1", "u-2", "u-3"));

            listener.notify(delegateTask);

            verify(taskService).addCandidateUser("task-1", "u-1");
            verify(taskService).addCandidateUser("task-1", "u-2");
            verify(taskService).addCandidateUser("task-1", "u-3");
        }

        @Test
        @DisplayName("resolver 解析为空列表应跳过")
        void shouldSkipWhenResolverReturnsEmpty() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_ROLE);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("nonexistent-role");
            when(assigneeResolver.resolveUserIds(anyString(), anyString()))
                    .thenReturn(Collections.emptyList());

            listener.notify(delegateTask);

            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }

        @Test
        @DisplayName("无 resolver 时 role 类型应跳过（不抛异常）")
        void shouldSkipRoleWhenNoResolver() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_ROLE);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("manager");

            listenerWithoutResolver.notify(delegateTask);

            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("assigneeType 为 null 应直接返回（不查询 value）")
        void shouldReturnEarlyWhenTypeNull() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(null);

            listener.notify(delegateTask);

            verify(runtimeService, never())
                    .getVariable(eq("exec-1"), eq(FlowConstant.EXT_ATTR_ASSIGNEE_VALUE));
            verify(taskService, never()).setAssignee(anyString(), anyString());
        }

        @Test
        @DisplayName("未知类型应跳过")
        void shouldSkipUnknownType() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn("unknown-type");
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("xxx");

            listener.notify(delegateTask);

            verify(taskService, never()).setAssignee(anyString(), anyString());
            verify(taskService, never()).addCandidateUser(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("任务级 BPMN 扩展属性：优先于流程变量（设计器产出）")
    class TaskAttributePriority {

        @Test
        @DisplayName("任务级属性存在时应逐任务生效，不理会流程变量")
        void shouldPreferTaskAttributes() {
            when(delegateTask.getProcessDefinitionId()).thenReturn("def-1");
            when(delegateTask.getTaskDefinitionKey()).thenReturn("task_1");
            when(repositoryService.getBpmnModel("def-1"))
                    .thenReturn(modelWithTaskAttributes(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager"));
            when(runtimeService.getVariable(anyString(), anyString())).thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(assigneeResolver.resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager"))
                    .thenReturn(List.of("u-role-1", "u-role-2"));

            listener.notify(delegateTask);

            verify(assigneeResolver).resolveUserIds(FlowConstant.ASSIGNEE_TYPE_ROLE, "manager");
            verify(taskService).addCandidateUser("task-1", "u-role-1");
            verify(taskService).addCandidateUser("task-1", "u-role-2");
            verify(taskService, never()).setAssignee(anyString(), anyString());
        }

        @Test
        @DisplayName("任务未配置扩展属性时应回退流程变量（兼容既有接入）")
        void shouldFallBackToVariables() {
            when(delegateTask.getProcessDefinitionId()).thenReturn("def-1");
            when(delegateTask.getTaskDefinitionKey()).thenReturn("task_1");
            when(repositoryService.getBpmnModel("def-1")).thenReturn(modelWithTaskAttributes(null, null));
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("u-100");

            listener.notify(delegateTask);

            verify(taskService).setAssignee("task-1", "u-100");
        }

        @Test
        @DisplayName("模型读取异常时应回退流程变量（不阻塞任务创建）")
        void shouldFallBackWhenModelUnavailable() {
            when(delegateTask.getProcessDefinitionId()).thenReturn("def-1");
            when(delegateTask.getTaskDefinitionKey()).thenReturn("task_1");
            when(repositoryService.getBpmnModel("def-1")).thenThrow(new RuntimeException("boom"));
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_INITIATOR);
            when(runtimeService.getVariable("exec-1", FlowConstant.VAR_INITIATOR_USER_ID))
                    .thenReturn("u-init");

            listener.notify(delegateTask);

            verify(taskService).setAssignee("task-1", "u-init");
        }

        @Test
        @DisplayName("无定义上下文时直接走流程变量（不触碰 RepositoryService）")
        void shouldSkipAttributeLookupWithoutDefinition() {
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_TYPE))
                    .thenReturn(FlowConstant.ASSIGNEE_TYPE_USER);
            when(runtimeService.getVariable("exec-1", FlowConstant.EXT_ATTR_ASSIGNEE_VALUE))
                    .thenReturn("u-100");

            listener.notify(delegateTask);

            verifyNoInteractions(repositoryService);
            verify(taskService).setAssignee("task-1", "u-100");
        }

        private BpmnModel modelWithTaskAttributes(String type, String value) {
            UserTask userTask = new UserTask();
            userTask.setId("task_1");
            if (type != null) {
                ExtensionAttribute typeAttr = new ExtensionAttribute(FlowConstant.EXT_ATTR_ASSIGNEE_TYPE);
                typeAttr.setValue(type);
                userTask.addAttribute(typeAttr);
            }
            if (value != null) {
                ExtensionAttribute valueAttr = new ExtensionAttribute(FlowConstant.EXT_ATTR_ASSIGNEE_VALUE);
                valueAttr.setValue(value);
                userTask.addAttribute(valueAttr);
            }
            Process process = new Process();
            process.setId("process_1");
            process.addFlowElement(userTask);
            BpmnModel model = new BpmnModel();
            model.addProcess(process);
            return model;
        }
    }
}
