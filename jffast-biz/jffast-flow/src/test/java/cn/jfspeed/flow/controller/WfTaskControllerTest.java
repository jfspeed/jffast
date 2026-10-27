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
package cn.jfspeed.flow.controller;

import cn.jfspeed.flow.service.FlowAccessService;
import cn.jfspeed.flowable.service.FlowService;
import tools.jackson.databind.json.JsonMapper;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WfTaskController MockMvc 单元测试。
 * <p>
 * 重点验证「任务接口返回的是 VO 而不是 Flowable 实体」：{@code Task} 接口声明了
 * {@code getProcessVariables()} / {@code getIdentityLinks()} 等会触发查询的 getter，
 * 直接返回给 Jackson 会把引擎内部结构吐出去。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WfTaskControllerTest {

    @Mock
    private FlowService flowService;
    @Mock
    private FlowAccessService flowAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // MockitoExtension 在 @BeforeEach 才初始化 @Mock
        when(flowAccessService.currentUserId()).thenReturn("100");
        WfTaskController controller = new WfTaskController(flowService, flowAccessService);
        JsonMapper objectMapper = JsonMapper.builder().build();
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    private Task mockTask() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("t-1");
        when(task.getName()).thenReturn("主管审批");
        when(task.getAssignee()).thenReturn("100");
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getProcessDefinitionId()).thenReturn("pd-1");
        when(task.getTaskDefinitionKey()).thenReturn("managerApprove");
        return task;
    }

    @Nested
    @DisplayName("待办/候选")
    class ListEndpoints {

        @Test
        @DisplayName("GET /todo/page 应返回 VO 并补上流程名称，且不泄漏引擎 getter")
        void shouldReturnVoForTodoPage() throws Exception {
            Task task = mockTask();
            when(flowService.countTodoTasks("100")).thenReturn(1L);
            when(flowService.listTodoTasks("100", 0, 10)).thenReturn(List.of(task));
            when(flowService.getProcessDefinitionName("pd-1")).thenReturn("请假流程");

            mockMvc.perform(get("/flow/task/todo/page").param("pageNo", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].id").value("t-1"))
                    .andExpect(jsonPath("$.data.list[0].name").value("主管审批"))
                    .andExpect(jsonPath("$.data.list[0].processDefinitionName").value("请假流程"))
                    // 引擎聚合属性不应出现在响应里
                    .andExpect(jsonPath("$.data.list[0].processVariables").doesNotExist())
                    .andExpect(jsonPath("$.data.list[0].identityLinks").doesNotExist());
        }

        @Test
        @DisplayName("GET /candidate/page 应返回 VO")
        void shouldReturnVoForCandidatePage() throws Exception {
            Task task = mockTask();
            when(flowService.countCandidateTasks("100")).thenReturn(1L);
            when(flowService.listCandidateTasks("100", 0, 10)).thenReturn(List.of(task));
            when(flowService.getProcessDefinitionName("pd-1")).thenReturn("请假流程");

            mockMvc.perform(get("/flow/task/candidate/page").param("pageNo", "1").param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.list[0].id").value("t-1"))
                    .andExpect(jsonPath("$.data.list[0].assignee").value("100"));
        }
    }

    @Nested
    @DisplayName("分页参数归一（P3-3）")
    class PaginationGuard {

        @Test
        @DisplayName("pageNo=0 不得给 Flowable 传负 offset")
        void pageNoZeroFallsBackToFirstPage() throws Exception {
            when(flowService.countTodoTasks("100")).thenReturn(0L);
            when(flowService.listTodoTasks("100", 0, 10)).thenReturn(List.of());

            mockMvc.perform(get("/flow/task/todo/page").param("pageNo", "0").param("pageSize", "10"))
                    .andExpect(status().isOk());

            verify(flowService).listTodoTasks(eq("100"), eq(0), eq(10));
        }

        @Test
        @DisplayName("负数 pageNo / 非正 pageSize 落默认值")
        void nonPositiveValuesFallBackToDefaults() throws Exception {
            when(flowService.countTodoTasks("100")).thenReturn(0L);
            when(flowService.listTodoTasks("100", 0, 10)).thenReturn(List.of());

            mockMvc.perform(get("/flow/task/todo/page").param("pageNo", "-3").param("pageSize", "0"))
                    .andExpect(status().isOk());

            verify(flowService).listTodoTasks(eq("100"), eq(0), eq(10));
        }

        @Test
        @DisplayName("pageSize 超过上限时收敛到 200")
        void oversizedPageSizeIsClamped() throws Exception {
            when(flowService.countCandidateTasks("100")).thenReturn(0L);
            when(flowService.listCandidateTasks("100", 0, 200)).thenReturn(List.of());

            mockMvc.perform(get("/flow/task/candidate/page").param("pageNo", "1").param("pageSize", "100000"))
                    .andExpect(status().isOk());

            verify(flowService).listCandidateTasks(eq("100"), eq(0), eq(200));
        }

        @Test
        @DisplayName("极大 pageNo 的 offset 不得整型溢出成负数")
        void hugePageNoDoesNotOverflow() throws Exception {
            when(flowService.countTodoTasks("100")).thenReturn(0L);
            when(flowService.listTodoTasks("100", Integer.MAX_VALUE, 200)).thenReturn(List.of());

            mockMvc.perform(get("/flow/task/todo/page")
                            .param("pageNo", String.valueOf(Integer.MAX_VALUE))
                            .param("pageSize", "200"))
                    .andExpect(status().isOk());

            verify(flowService).listTodoTasks(eq("100"), eq(Integer.MAX_VALUE), eq(200));
        }
    }

    @Nested
    @DisplayName("详情/历史")
    class DetailEndpoints {

        @Test
        @DisplayName("GET /detail/{taskId} 应返回 VO")
        void shouldReturnVoForDetail() throws Exception {
            Task task = mockTask();
            when(flowService.getTask("t-1")).thenReturn(task);
            when(flowService.getProcessDefinitionName("pd-1")).thenReturn("请假流程");

            mockMvc.perform(get("/flow/task/detail/t-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("t-1"))
                    .andExpect(jsonPath("$.data.processDefinitionName").value("请假流程"))
                    .andExpect(jsonPath("$.data.processVariables").doesNotExist());
        }

        @Test
        @DisplayName("GET /detail/{taskId} 任务不存在时应返回 data=null")
        void shouldReturnNullWhenTaskMissing() throws Exception {
            when(flowService.getTask("t-x")).thenReturn(null);

            mockMvc.perform(get("/flow/task/detail/t-x"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("GET /history/{processInstanceId} 应返回历史任务 VO 列表")
        void shouldReturnVoForTaskHistory() throws Exception {
            HistoricTaskInstance historic = mock(HistoricTaskInstance.class);
            when(historic.getId()).thenReturn("t-1");
            when(historic.getName()).thenReturn("主管审批");
            when(historic.getAssignee()).thenReturn("100");
            when(historic.getProcessInstanceId()).thenReturn("pi-1");
            when(flowService.listProcessHistory("pi-1")).thenReturn(List.of(historic));

            mockMvc.perform(get("/flow/task/history/pi-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].id").value("t-1"))
                    .andExpect(jsonPath("$.data[0].assignee").value("100"));
        }
    }
}
