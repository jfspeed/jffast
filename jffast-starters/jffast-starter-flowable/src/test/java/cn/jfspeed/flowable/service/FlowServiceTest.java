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
package cn.jfspeed.flowable.service;

import cn.jfspeed.flowable.constant.FlowConstant;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FlowService 单元测试。
 * <p>
 * 纯 Mockito，不启动 Spring 上下文，不连数据库。
 * 验证 FlowService 对 Flowable 四大服务的封装逻辑正确。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowServiceTest {

    @Mock
    private RepositoryService repositoryService;
    @Mock
    private RuntimeService runtimeService;
    @Mock
    private TaskService taskService;
    @Mock
    private HistoryService historyService;
    @Mock
    private IdentityService identityService;

    @InjectMocks
    private FlowService flowService;

    // ─── 流程部署 ────────────────────────────────────────

    @Nested
    @DisplayName("流程部署")
    class Deploy {

        // 部署前会预校验 BPMN 根元素必须为 definitions，故这里用合法的最小 BPMN
        private static final String BPMN_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"></definitions>";

        @Test
        @DisplayName("deploy(name, resourceName, bpmnXml) 应通过 RepositoryService 部署 XML 字符串")
        void shouldDeployBpmnXmlString() {
            DeploymentBuilder builder = mock(DeploymentBuilder.class);
            Deployment deployment = mock(Deployment.class);
            when(repositoryService.createDeployment()).thenReturn(builder);
            when(builder.name(anyString())).thenReturn(builder);
            when(builder.addString(anyString(), anyString())).thenReturn(builder);
            when(builder.deploy()).thenReturn(deployment);

            Deployment result = flowService.deploy("请假流程", null, "leave.bpmn20.xml", BPMN_XML);

            assertSame(deployment, result);
            verify(repositoryService).createDeployment();
            verify(builder).name("请假流程");
            verify(builder).addString("leave.bpmn20.xml", BPMN_XML);
            verify(builder).deploy();
            // category 为空时不应触碰流程定义分类
            verify(repositoryService, never()).setProcessDefinitionCategory(anyString(), anyString());
        }

        @Test
        @DisplayName("deploy 带 category 时应把分类写到流程定义上")
        void shouldApplyCategoryToProcessDefinitions() {
            DeploymentBuilder builder = mock(DeploymentBuilder.class);
            Deployment deployment = mock(Deployment.class);
            when(deployment.getId()).thenReturn("dep-1");
            ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);
            ProcessDefinition definition = mock(ProcessDefinition.class);
            when(definition.getId()).thenReturn("pd-1");
            when(repositoryService.createDeployment()).thenReturn(builder);
            when(builder.name(anyString())).thenReturn(builder);
            when(builder.addString(anyString(), anyString())).thenReturn(builder);
            when(builder.deploy()).thenReturn(deployment);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
            when(pdQuery.deploymentId("dep-1")).thenReturn(pdQuery);
            when(pdQuery.list()).thenReturn(List.of(definition));

            flowService.deploy("请假流程", "leave", "leave.bpmn20.xml", BPMN_XML);

            verify(repositoryService).setProcessDefinitionCategory("pd-1", "leave");
        }

        @Test
        @DisplayName("deploy(name, resourceName, InputStream) 应通过 RepositoryService 部署输入流")
        void shouldDeployInputStream() {
            DeploymentBuilder builder = mock(DeploymentBuilder.class);
            Deployment deployment = mock(Deployment.class);
            InputStream in = new ByteArrayInputStream(BPMN_XML.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.createDeployment()).thenReturn(builder);
            when(builder.name(anyString())).thenReturn(builder);
            when(builder.addInputStream(anyString(), any())).thenReturn(builder);
            when(builder.deploy()).thenReturn(deployment);

            Deployment result = flowService.deploy("请假流程", null, "leave.bpmn20.xml", in);

            assertSame(deployment, result);
            verify(builder).addInputStream(eq("leave.bpmn20.xml"), any(InputStream.class));
        }

        @Test
        @DisplayName("deploy 输入流非 BPMN 根元素应拒绝（BusinessException），不触达引擎")
        void shouldRejectNonBpmnXmlFromInputStream() {
            InputStream in = new ByteArrayInputStream("<xml/>".getBytes(StandardCharsets.UTF_8));
            assertThrows(cn.jfspeed.common.core.exception.BusinessException.class,
                    () -> flowService.deploy("请假流程", null, "leave.bpmn20.xml", in));
            verify(repositoryService, never()).createDeployment();
        }

        @Test
        @DisplayName("deleteDeployment 应级联删除")
        void shouldDeleteDeploymentWithCascade() {
            flowService.deleteDeployment("dep-1", true);
            verify(repositoryService).deleteDeployment("dep-1", true);
        }

        @Test
        @DisplayName("deploy 资源名含路径穿越应拒绝（BusinessException），不触达引擎")
        void shouldRejectPathTraversalResourceName() {
            assertThrows(cn.jfspeed.common.core.exception.BusinessException.class,
                    () -> flowService.deploy("请假流程", null, "../evil.bpmn20.xml", BPMN_XML));
            verify(repositoryService, never()).createDeployment();
        }

        @Test
        @DisplayName("deploy 非 BPMN 根元素应拒绝（BusinessException），不触达引擎")
        void shouldRejectNonBpmnXml() {
            assertThrows(cn.jfspeed.common.core.exception.BusinessException.class,
                    () -> flowService.deploy("请假流程", null, "leave.bpmn20.xml", "<xml/>"));
            verify(repositoryService, never()).createDeployment();
        }
    }

    // ─── BPMN XML 读取 ─────────────────────────────────

    @Nested
    @DisplayName("BPMN XML 读取")
    class BpmnXmlRead {

        @Test
        @DisplayName("getBpmnXml 应正确读取 InputStream 为 UTF-8 字符串")
        void shouldReadBpmnXml() throws IOException {
            String xml = "<definitions/>";
            InputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            when(repositoryService.getProcessModel("pd-1")).thenReturn(in);

            String result = flowService.getBpmnXml("pd-1");

            assertEquals(xml, result);
        }

        @Test
        @DisplayName("getBpmnXml 流为 null 时应返回 null")
        void shouldReturnNullWhenStreamIsNull() {
            when(repositoryService.getProcessModel("pd-1")).thenReturn(null);
            assertNull(flowService.getBpmnXml("pd-1"));
        }

        @Test
        @DisplayName("getBpmnXml 读取失败应抛 RuntimeException")
        void shouldThrowOnReadFailure() throws IOException {
            InputStream in = mock(InputStream.class);
            when(repositoryService.getProcessModel("pd-1")).thenReturn(in);
            when(in.readAllBytes()).thenThrow(new IOException("disk error"));

            assertThrows(RuntimeException.class, () -> flowService.getBpmnXml("pd-1"));
        }

        @Test
        @DisplayName("getBpmnXml 应关闭 InputStream（try-with-resources）")
        void shouldCloseInputStream() throws IOException {
            InputStream in = spy(new ByteArrayInputStream("<xml/>".getBytes(StandardCharsets.UTF_8)));
            when(repositoryService.getProcessModel("pd-1")).thenReturn(in);

            flowService.getBpmnXml("pd-1");

            verify(in).close();
        }
    }

    // ─── 流程定义查询 ───────────────────────────────────

    @Nested
    @DisplayName("流程定义查询")
    class ProcessDefinitionQueryOps {

        @Test
        @DisplayName("listProcessDefinitions 应调用 latestVersion + 分页")
        void shouldListProcessDefinitionsPaginated() {
            ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
            when(query.latestVersion()).thenReturn(query);
            when(query.orderByProcessDefinitionKey()).thenReturn(query);
            when(query.asc()).thenReturn(query);
            when(query.processDefinitionKeyLike("%leave%")).thenReturn(query);
            List<ProcessDefinition> mockList = List.of(mock(ProcessDefinition.class));
            when(query.listPage(0, 10)).thenReturn(mockList);

            List<ProcessDefinition> result = flowService.listProcessDefinitions("leave", null, 0, 10);

            assertSame(mockList, result);
            verify(query).latestVersion();
            verify(query).processDefinitionKeyLike("%leave%");
            verify(query).listPage(0, 10);
            verify(query, never()).list();
        }

        @Test
        @DisplayName("listProcessDefinitions 无分页参数应调用 list()")
        void shouldListAllWhenNoPagination() {
            ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
            when(query.latestVersion()).thenReturn(query);
            when(query.orderByProcessDefinitionKey()).thenReturn(query);
            when(query.asc()).thenReturn(query);
            List<ProcessDefinition> mockList = List.of(mock(ProcessDefinition.class));
            when(query.list()).thenReturn(mockList);

            List<ProcessDefinition> result = flowService.listProcessDefinitions(null, null, null, null);

            assertSame(mockList, result);
            verify(query).list();
            verify(query, never()).listPage(anyInt(), anyInt());
        }

        @Test
        @DisplayName("countProcessDefinitions 应调用 count()")
        void shouldCountProcessDefinitions() {
            ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
            when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
            when(query.latestVersion()).thenReturn(query);
            when(query.count()).thenReturn(42L);

            long count = flowService.countProcessDefinitions(null, null);

            assertEquals(42L, count);
        }

        @Test
        @DisplayName("suspendProcessDefinition 应调用 suspendProcessDefinitionById")
        void shouldSuspendProcessDefinition() {
            flowService.suspendProcessDefinition("pd-1");
            verify(repositoryService).suspendProcessDefinitionById("pd-1", true, null);
        }

        @Test
        @DisplayName("activateProcessDefinition 应调用 activateProcessDefinitionById")
        void shouldActivateProcessDefinition() {
            flowService.activateProcessDefinition("pd-1");
            verify(repositoryService).activateProcessDefinitionById("pd-1", true, null);
        }

        @Test
        @DisplayName("getProcessDefinitionName 应返回定义名称，查不到返回 null")
        void shouldGetProcessDefinitionName() {
            ProcessDefinition definition = mock(ProcessDefinition.class);
            when(definition.getName()).thenReturn("请假流程");
            when(repositoryService.getProcessDefinition("pd-1")).thenReturn(definition);

            assertEquals("请假流程", flowService.getProcessDefinitionName("pd-1"));
            assertNull(flowService.getProcessDefinitionName(null));
            when(repositoryService.getProcessDefinition("pd-x")).thenReturn(null);
            assertNull(flowService.getProcessDefinitionName("pd-x"));
        }
    }

    // ─── 流程实例 ───────────────────────────────────────

    @Nested
    @DisplayName("流程实例")
    class ProcessInstanceOps {

        @Test
        @DisplayName("startProcess 应自动写入 initiatorUserId 变量并设置 Flowable 认证用户")
        void shouldStartProcessWithInitiatorVar() {
            ProcessInstance instance = mock(ProcessInstance.class);
            when(runtimeService.startProcessInstanceByKey(eq("leave"), eq("BIZ-1"), anyMap()))
                    .thenReturn(instance);

            ProcessInstance result = flowService.startProcess("leave", "BIZ-1", "u-100",
                    new java.util.HashMap<>(Map.of("days", 3)));

            assertSame(instance, result);
            // 验证 variables 中写入了 initiatorUserId
            verify(runtimeService).startProcessInstanceByKey(eq("leave"), eq("BIZ-1"),
                    argThat(vars -> "u-100".equals(vars.get(FlowConstant.VAR_INITIATOR_USER_ID))));
            // 验证设置了认证用户（引擎据此写入 startUserId），且用后清理
            verify(identityService).setAuthenticatedUserId("u-100");
            verify(identityService).setAuthenticatedUserId(null);
        }

        @Test
        @DisplayName("startProcess variables 为 null 时不应抛 NPE，且仍写入 initiatorUserId")
        void shouldHandleNullVariables() {
            ProcessInstance instance = mock(ProcessInstance.class);
            when(runtimeService.startProcessInstanceByKey(anyString(), any(), anyMap()))
                    .thenReturn(instance);

            assertDoesNotThrow(() -> flowService.startProcess("leave", null, "u-100", null));

            verify(runtimeService).startProcessInstanceByKey(eq("leave"), isNull(),
                    argThat(vars -> "u-100".equals(vars.get(FlowConstant.VAR_INITIATOR_USER_ID))));
        }

        @Test
        @DisplayName("startProcess 引擎抛异常时应清理认证用户（finally）")
        void shouldClearAuthenticatedUserOnFailure() {
            when(runtimeService.startProcessInstanceByKey(anyString(), any(), anyMap()))
                    .thenThrow(new RuntimeException("engine down"));

            assertThrows(RuntimeException.class,
                    () -> flowService.startProcess("leave", null, "u-100", null));

            verify(identityService).setAuthenticatedUserId("u-100");
            verify(identityService).setAuthenticatedUserId(null);
        }

        @Test
        @DisplayName("countProcessInstances 应正确过滤 processKey/initiatorUserId")
        void shouldCountProcessInstancesWithFilters() {
            ProcessInstanceQuery query = mock(ProcessInstanceQuery.class);
            when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
            when(query.processDefinitionKey("leave")).thenReturn(query);
            when(query.startedBy("u-100")).thenReturn(query);
            when(query.count()).thenReturn(5L);

            long count = flowService.countProcessInstances("leave", "u-100");

            assertEquals(5L, count);
            verify(query).processDefinitionKey("leave");
            verify(query).startedBy("u-100");
        }

        @Test
        @DisplayName("cancelProcessInstance 应调用 deleteProcessInstance")
        void shouldCancelProcessInstance() {
            flowService.cancelProcessInstance("pi-1", "用户取消");
            verify(runtimeService).deleteProcessInstance("pi-1", "用户取消");
        }
    }

    // ─── 任务 ───────────────────────────────────────────

    @Nested
    @DisplayName("任务管理")
    class TaskOps {

        @Test
        @DisplayName("completeTask 任务不存在应抛 IllegalArgumentException")
        void shouldThrowWhenTaskNotFound() {
            TaskQuery query = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.taskId("t-1")).thenReturn(query);
            when(query.singleResult()).thenReturn(null);

            assertThrows(IllegalArgumentException.class,
                    () -> flowService.completeTask("t-1", "u-1", "ok", null));
        }

        @Test
        @DisplayName("completeTask 带 comment 应调用 addComment")
        void shouldAddCommentWhenPresent() {
            TaskQuery query = mock(TaskQuery.class);
            Task task = mock(Task.class);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.taskId("t-1")).thenReturn(query);
            when(query.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn("pi-1");

            flowService.completeTask("t-1", "u-1", "同意", null);

            verify(taskService).addComment("t-1", "pi-1", "同意");
            verify(taskService).complete("t-1");
        }

        @Test
        @DisplayName("completeTask 带 variables 应调用 complete(taskId, variables)")
        void shouldCompleteWithVariables() {
            TaskQuery query = mock(TaskQuery.class);
            Task task = mock(Task.class);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.taskId("t-1")).thenReturn(query);
            when(query.singleResult()).thenReturn(task);
            when(task.getProcessInstanceId()).thenReturn("pi-1");

            Map<String, Object> vars = Map.of("approve", true);
            flowService.completeTask("t-1", "u-1", null, vars);

            verify(taskService, never()).addComment(anyString(), anyString(), anyString());
            verify(taskService).complete("t-1", vars);
        }

        @Test
        @DisplayName("claimTask 应调用 taskService.claim")
        void shouldClaimTask() {
            flowService.claimTask("t-1", "u-100");
            verify(taskService).claim("t-1", "u-100");
        }

        @Test
        @DisplayName("delegateTask 应调用 setAssignee（转办）")
        void shouldDelegateTask() {
            flowService.delegateTask("t-1", "u-200");
            verify(taskService).setAssignee("t-1", "u-200");
        }

        @Test
        @DisplayName("countTodoTasks 应按 assignee 查询 count")
        void shouldCountTodoTasks() {
            TaskQuery query = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.taskAssignee("u-1")).thenReturn(query);
            when(query.count()).thenReturn(3L);

            assertEquals(3L, flowService.countTodoTasks("u-1"));
        }

        @Test
        @DisplayName("countCandidateTasks 应按 candidateUser 查询 count")
        void shouldCountCandidateTasks() {
            TaskQuery query = mock(TaskQuery.class);
            when(taskService.createTaskQuery()).thenReturn(query);
            when(query.taskCandidateUser("u-1")).thenReturn(query);
            when(query.count()).thenReturn(7L);

            assertEquals(7L, flowService.countCandidateTasks("u-1"));
        }
    }

    // ─── 历史 ───────────────────────────────────────────

    @Nested
    @DisplayName("历史查询")
    class HistoryOps {

        @Test
        @DisplayName("countDoneTasks 应按 assignee + finished 查询")
        void shouldCountDoneTasks() {
            HistoricTaskInstanceQuery query = mock(HistoricTaskInstanceQuery.class);
            when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
            when(query.taskAssignee("u-1")).thenReturn(query);
            when(query.finished()).thenReturn(query);
            when(query.count()).thenReturn(9L);

            assertEquals(9L, flowService.countDoneTasks("u-1"));
            verify(query).finished();
        }

        @Test
        @DisplayName("countHistoricProcessInstances 应正确过滤")
        void shouldCountHistoricProcessInstances() {
            HistoricProcessInstanceQuery query = mock(HistoricProcessInstanceQuery.class);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(query);
            when(query.processDefinitionKey("leave")).thenReturn(query);
            when(query.startedBy("u-1")).thenReturn(query);
            when(query.count()).thenReturn(2L);

            assertEquals(2L, flowService.countHistoricProcessInstances("leave", "u-1"));
        }
    }
}
