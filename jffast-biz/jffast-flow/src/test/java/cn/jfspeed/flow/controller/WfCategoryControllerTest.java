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

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.json.JsonMapper;

import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.flow.entity.WfCategory;
import cn.jfspeed.flow.service.WfCategoryService;

/**
 * WfCategoryController MockMvc 单元测试。
 * <p>
 * standalone 模式：不启动 Spring 上下文，不处理 Sa-Token/RateLimit/DistributedLock 注解，
 * 聚焦验证 6 个端点的请求路由 + JSON 反序列化 + WfCategoryService 调用契约 + R 响应格式。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WfCategoryControllerTest {

    @Mock
    private WfCategoryService service;

    @InjectMocks
    private WfCategoryController controller;

    private MockMvc mockMvc;
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 BaseController.service
        ReflectionTestUtils.setField(controller, "service", service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private WfCategory buildCategory(Long id, String code, String name) {
        WfCategory c = new WfCategory();
        c.setId(id);
        c.setCategoryCode(code);
        c.setCategoryName(name);
        c.setSort(1);
        c.setStatus(0);
        return c;
    }

    @Nested
    @DisplayName("GET /flow/category/page")
    class PageEndpoint {

        @Test
        @DisplayName("应返回分页结构 {total, list}")
        void shouldReturnPage() throws Exception {
            PageRes<WfCategory> pageRes = new PageRes<>(2L, 1L, Arrays.asList(
                    buildCategory(1L, "leave", "请假"),
                    buildCategory(2L, "expense", "报销")));
            when(service.page(eq("请假"), isNull(), any(PageReq.class))).thenReturn(pageRes);

            mockMvc.perform(get("/flow/category/page")
                            .param("categoryName", "请假")
                            .param("pageNo", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.list[0].categoryCode").value("leave"));
        }

        @Test
        @DisplayName("无条件查询应正常返回")
        void shouldReturnPageWithoutCondition() throws Exception {
            PageRes<WfCategory> pageRes = new PageRes<>(0L, 0L, Collections.emptyList());
            when(service.page(isNull(), isNull(), any(PageReq.class))).thenReturn(pageRes);

            mockMvc.perform(get("/flow/category/page")
                            .param("pageNo", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    @Nested
    @DisplayName("GET /flow/category/list")
    class ListEndpoint {

        @Test
        @DisplayName("应返回启用分类列表")
        void shouldReturnList() throws Exception {
            when(service.listEnabled()).thenReturn(Arrays.asList(
                    buildCategory(1L, "leave", "请假"),
                    buildCategory(2L, "expense", "报销")));

            mockMvc.perform(get("/flow/category/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].categoryCode").value("leave"))
                    .andExpect(jsonPath("$.data[1].categoryCode").value("expense"));
        }
    }

    @Nested
    @DisplayName("GET /flow/category/detail/{id}")
    class DetailEndpoint {

        @Test
        @DisplayName("应返回分类详情")
        void shouldReturnDetail() throws Exception {
            when(service.getById(1L)).thenReturn(buildCategory(1L, "leave", "请假"));

            mockMvc.perform(get("/flow/category/detail/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.categoryCode").value("leave"));
        }
    }

    @Nested
    @DisplayName("POST /flow/category/create")
    class CreateEndpoint {

        @Test
        @DisplayName("应保存并返回成功")
        void shouldCreate() throws Exception {
            when(service.save(any(WfCategory.class))).thenReturn(true);

            mockMvc.perform(post("/flow/category/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCategory(null, "leave", "请假"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(service, times(1)).save(any(WfCategory.class));
        }
    }

    @Nested
    @DisplayName("PUT /flow/category/update")
    class UpdateEndpoint {

        @Test
        @DisplayName("应更新并返回成功")
        void shouldUpdate() throws Exception {
            when(service.updateById(any(WfCategory.class))).thenReturn(true);

            mockMvc.perform(put("/flow/category/update")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildCategory(1L, "leave", "请假审批"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(service, times(1)).updateById(any(WfCategory.class));
        }
    }

    @Nested
    @DisplayName("DELETE /flow/category/{id}")
    class DeleteEndpoint {

        @Test
        @DisplayName("应删除并返回成功")
        void shouldDelete() throws Exception {
            when(service.removeById(1L)).thenReturn(true);

            mockMvc.perform(delete("/flow/category/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(service, times(1)).removeById(1L);
        }
    }
}
