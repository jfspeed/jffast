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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import cn.jfspeed.flow.domain.dto.WfDeployRequest;
import cn.jfspeed.flow.domain.dto.WfStartProcessRequest;
import cn.jfspeed.flow.service.FlowAccessService;
import cn.jfspeed.flowable.service.FlowService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WfProcessController MockMvc 单元测试。
 * <p>
 * standalone 模式：不启动 Spring 上下文，不处理 Sa-Token/RateLimit/DistributedLock 注解，
 * 聚焦验证请求路由 + JSON 反序列化 + FlowService 调用契约 + R 响应格式。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WfProcessControllerTest {

    @Mock
    private FlowService flowService;
    @Mock
    private FlowAccessService flowAccessService;

    private MockMvc mockMvc;
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        // MockitoExtension 在 @BeforeEach 才初始化 @Mock，不能在构造函数中使用 flowService
        when(flowAccessService.currentUserId()).thenReturn("100");
        WfProcessController controller = new WfProcessController(flowService, flowAccessService);
        // 注册 ProcessDefinition 自定义序列化器，只输出业务字段，
        // 避免 Jackson 序列化 Mockito mock 内部（ByteBuddyCrossClassLoaderSerializationSupport）失败
        SimpleModule module = new SimpleModule();
        module.addSerializer(ProcessDefinition.class, new ValueSerializer<ProcessDefinition>() {
            @Override
            public void serialize(ProcessDefinition value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeStartObject();
                if (value.getId() != null) {
                    gen.writeStringProperty("id", value.getId());
                }
                if (value.getKey() != null) {
                    gen.writeStringProperty("key", value.getKey());
                }
                if (value.getName() != null) {
                    gen.writeStringProperty("name", value.getName());
                }
                gen.writeNumberProperty("version", value.getVersion());
                gen.writeEndObject();
            }
        });
        objectMapper = JsonMapper.builder().addModule(module).build();
        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(converter)
                .build();
    }

    @Nested
    @DisplayName("流程部署")
    class DeployEndpoint {

        @Test
        @DisplayName("POST /deploy 应透传分类并返回部署 ID")
        void shouldDeployAndReturnId() throws Exception {
            Deployment deployment = mock(Deployment.class);
            when(deployment.getId()).thenReturn("dep-123");
            when(flowService.deploy(any(), any(), anyString(), anyString()))
                    .thenReturn(deployment);

            WfDeployRequest req = new WfDeployRequest();
            req.setName("请假流程");
            req.setCategory("leave");
            req.setResourceName("leave.bpmn20.xml");
            req.setBpmnXml("<xml/>");

            mockMvc.perform(post("/flow/process/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("dep-123"));

            // 分类必须透传给服务层（否则流程定义 category 落不到 wf_category.category_code）
            verify(flowService).deploy("请假流程", "leave", "leave.bpmn20.xml", "<xml/>");
        }

        @Test
        @DisplayName("resourceName 为空时应使用默认值 process.bpmn20.xml")
        void shouldUseDefaultResourceNameWhenNull() throws Exception {
            Deployment deployment = mock(Deployment.class);
            when(deployment.getId()).thenReturn("dep-1");
            when(flowService.deploy(any(), any(), eq("process.bpmn20.xml"), anyString())).thenReturn(deployment);

            WfDeployRequest req = new WfDeployRequest();
            req.setName("test");
            req.setBpmnXml("<xml/>");
            // resourceName 不设置

            mockMvc.perform(post("/flow/process/deploy")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("删除部署")
    class DeleteDeploymentEndpoint {

        @Test
        @DisplayName("DELETE /deployment/{id} 应级联删除并返回成功")
        void shouldDeleteDeploymentWithCascade() throws Exception {
            mockMvc.perform(delete("/flow/process/deployment/dep-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(flowService).deleteDeployment("dep-1", true);
        }
    }

    @Nested
    @DisplayName("流程定义查询")
    class DefinitionQueryEndpoint {

        @Test
        @DisplayName("GET /definition/page 应返回分页结构 {total, list}")
        void shouldReturnPageStructure() throws Exception {
            ProcessDefinition def = mock(ProcessDefinition.class);
            when(def.getId()).thenReturn("pd-1");
            when(def.getKey()).thenReturn("leave");
            when(def.getName()).thenReturn("请假流程");
            when(def.getVersion()).thenReturn(1);
            when(flowService.countProcessDefinitions("leave", null)).thenReturn(1L);
            when(flowService.listProcessDefinitions(eq("leave"), isNull(), eq(0), eq(10)))
                    .thenReturn(Collections.singletonList(def));

            mockMvc.perform(get("/flow/process/definition/page")
                            .param("processKey", "leave")
                            .param("pageNo", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.list[0].id").value("pd-1"));
        }

        @Test
        @DisplayName("GET /definition/{id}/xml 应返回 XML 字符串")
        void shouldReturnBpmnXml() throws Exception {
            when(flowService.getBpmnXml("pd-1")).thenReturn("<definitions/>");

            mockMvc.perform(get("/flow/process/definition/pd-1/xml"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("<definitions/>"));
        }

        @Test
        @DisplayName("PUT /definition/{id}/suspend 应挂起并返回成功")
        void shouldSuspendDefinition() throws Exception {
            mockMvc.perform(put("/flow/process/definition/pd-1/suspend"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(flowService).suspendProcessDefinition("pd-1");
        }

        @Test
        @DisplayName("PUT /definition/{id}/activate 应激活并返回成功")
        void shouldActivateDefinition() throws Exception {
            mockMvc.perform(put("/flow/process/definition/pd-1/activate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(flowService).activateProcessDefinition("pd-1");
        }
    }

    @Nested
    @DisplayName("流程实例")
    class InstanceEndpoint {

        @Test
        @DisplayName("POST /instance/start 应使用当前登录用户作为发起人")
        void shouldStartInstanceWithLoginUser() throws Exception {
            ProcessInstance instance = mock(ProcessInstance.class);
            when(instance.getId()).thenReturn("pi-1");
            when(flowService.startProcess(eq("leave"), eq("BIZ-1"), eq("100"), anyMap()))
                    .thenReturn(instance);

            WfStartProcessRequest req = new WfStartProcessRequest();
            req.setProcessKey("leave");
            req.setBusinessKey("BIZ-1");
            Map<String, Object> vars = new HashMap<>();
            vars.put("days", 3);
            req.setVariables(vars);

            mockMvc.perform(post("/flow/process/instance/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("pi-1"));
        }

        @Test
        @DisplayName("GET /instance/page 非超管只能按本人发起人过滤")
        void shouldForceSelfInitiatorForNonSuperAdmin() throws Exception {
            when(flowAccessService.isSuperAdmin()).thenReturn(false);
            when(flowService.countProcessInstances("leave", "100")).thenReturn(0L);
            when(flowService.listProcessInstances("leave", "100", 0, 10))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/flow/process/instance/page")
                            .param("processKey", "leave")
                            .param("initiatorUserId", "999")
                            .param("pageNo", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));

            verify(flowService).countProcessInstances("leave", "100");
        }

        @Test
        @DisplayName("GET /instance/page 应返回分页结构")
        void shouldReturnInstancePage() throws Exception {
            when(flowAccessService.isSuperAdmin()).thenReturn(true);
            when(flowService.countProcessInstances(null, null)).thenReturn(0L);
            when(flowService.listProcessInstances(isNull(), isNull(), eq(0), eq(10)))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/flow/process/instance/page")
                            .param("pageNo", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));
        }

        @Test
        @DisplayName("DELETE /instance/{id} 应取消并支持 reason 参数")
        void shouldCancelInstanceWithReason() throws Exception {
            HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
            when(instance.getEndTime()).thenReturn(null);
            when(flowService.getHistoricProcessInstance("pi-1")).thenReturn(instance);

            mockMvc.perform(delete("/flow/process/instance/pi-1").param("reason", "用户取消"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(flowAccessService).checkProcessCancelable("pi-1", "100");
            verify(flowService).cancelProcessInstance("pi-1", "用户取消");
        }
    }
}
