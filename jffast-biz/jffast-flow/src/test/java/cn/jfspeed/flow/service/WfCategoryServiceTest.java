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
package cn.jfspeed.flow.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.flow.entity.WfCategory;
import cn.jfspeed.flow.mapper.WfCategoryMapper;

/**
 * WfCategoryService 单元测试。
 * <p>
 * 验证 page 分页查询与 listEnabled 启用列表逻辑：分页参数传递、条件构造（LambdaQueryWrapper 列名）、
 * 返回值转换（PageRes）。通过 @BeforeAll 初始化 MyBatis-Plus TableInfo，使 LambdaQueryWrapper
 * 能解析实体字段到列名。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WfCategoryServiceTest {

    @Mock
    private WfCategoryMapper mapper;

    @InjectMocks
    private WfCategoryService service;

    @BeforeAll
    static void initTableInfo() {
        // 初始化 MyBatis-Plus TableInfo，使 LambdaQueryWrapper 能解析实体列名
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WfCategory.class);
    }

    @BeforeEach
    void injectBaseMapper() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    private WfCategory buildCategory(Long id, String code, String name, Integer sort, Integer status) {
        WfCategory c = new WfCategory();
        c.setId(id);
        c.setCategoryCode(code);
        c.setCategoryName(name);
        c.setSort(sort);
        c.setStatus(status);
        return c;
    }

    @Nested
    @DisplayName("page：分页查询")
    class PageQuery {

        @Test
        @DisplayName("应返回分页结果并调用 mapper.selectPage")
        void shouldReturnPageResult() {
            PageReq pageReq = new PageReq(1, 10);
            Page<WfCategory> mpPage = new Page<>(1, 10, 2);
            mpPage.setRecords(Arrays.asList(
                    buildCategory(1L, "leave", "请假", 1, 0),
                    buildCategory(2L, "expense", "报销", 2, 0)));
            when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mpPage);

            PageRes<WfCategory> result = service.page("请假", null, pageReq);

            assertEquals(2L, result.getTotal());
            assertEquals(2, result.getList().size());
            assertEquals("leave", result.getList().get(0).getCategoryCode());
            verify(mapper, times(1)).selectPage(any(IPage.class), any(Wrapper.class));
        }

        @Test
        @DisplayName("查询结果为空时应返回空 list")
        void shouldReturnEmptyWhenNoData() {
            PageReq pageReq = new PageReq(1, 10);
            Page<WfCategory> mpPage = new Page<>(1, 10, 0);
            mpPage.setRecords(Collections.emptyList());
            when(mapper.selectPage(any(IPage.class), any(Wrapper.class))).thenReturn(mpPage);

            PageRes<WfCategory> result = service.page(null, null, pageReq);

            assertEquals(0L, result.getTotal());
            assertTrue(result.getList().isEmpty());
        }

        @Test
        @DisplayName("应将分页参数 pageNo/pageSize 传递给 mapper")
        void shouldPassPageParamsToMapper() {
            PageReq pageReq = new PageReq(2, 20);
            Page<WfCategory> mpPage = new Page<>(2, 20, 0);
            mpPage.setRecords(Collections.emptyList());
            ArgumentCaptor<IPage> pageCaptor = ArgumentCaptor.forClass(IPage.class);
            when(mapper.selectPage(pageCaptor.capture(), any(Wrapper.class))).thenReturn(mpPage);

            service.page(null, null, pageReq);

            IPage<?> captured = pageCaptor.getValue();
            assertEquals(2L, captured.getCurrent());
            assertEquals(20L, captured.getSize());
        }

        @Test
        @DisplayName("传入分类名称时 wrapper 应包含 category_name 条件")
        void shouldBuildCategoryNameLikeCondition() {
            PageReq pageReq = new PageReq(1, 10);
            Page<WfCategory> mpPage = new Page<>(1, 10, 0);
            mpPage.setRecords(Collections.emptyList());
            ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            when(mapper.selectPage(any(IPage.class), wrapperCaptor.capture())).thenReturn(mpPage);

            service.page("请假", null, pageReq);

            String sql = wrapperCaptor.getValue().getSqlSegment();
            assertTrue(sql.contains("category_name"), "wrapper 应包含 category_name 列条件");
        }

        @Test
        @DisplayName("传入分类编码时 wrapper 应包含 category_code 条件")
        void shouldBuildCategoryCodeEqCondition() {
            PageReq pageReq = new PageReq(1, 10);
            Page<WfCategory> mpPage = new Page<>(1, 10, 0);
            mpPage.setRecords(Collections.emptyList());
            ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
            when(mapper.selectPage(any(IPage.class), wrapperCaptor.capture())).thenReturn(mpPage);

            service.page(null, "leave", pageReq);

            String sql = wrapperCaptor.getValue().getSqlSegment();
            assertTrue(sql.contains("category_code"), "wrapper 应包含 category_code 列条件");
        }
    }

    @Nested
    @DisplayName("listEnabled：查询启用分类")
    class ListEnabled {

        @Test
        @DisplayName("应返回 status=0 的分类列表")
        void shouldReturnEnabledCategories() {
            List<WfCategory> categories = Arrays.asList(
                    buildCategory(1L, "leave", "请假", 1, 0),
                    buildCategory(2L, "expense", "报销", 2, 0));
            when(mapper.selectList(any(Wrapper.class))).thenReturn(categories);

            List<WfCategory> result = service.listEnabled();

            assertEquals(2, result.size());
            assertEquals("leave", result.get(0).getCategoryCode());
            verify(mapper, times(1)).selectList(any(Wrapper.class));
        }

        @Test
        @DisplayName("无启用分类时应返回空列表")
        void shouldReturnEmptyWhenNoneEnabled() {
            when(mapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());

            List<WfCategory> result = service.listEnabled();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("wrapper 应包含 status 条件与 sort 排序")
        void shouldBuildStatusAndSortCondition() {
            when(mapper.selectList(any(Wrapper.class))).thenReturn(Collections.emptyList());
            ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);

            service.listEnabled();

            verify(mapper).selectList(wrapperCaptor.capture());
            String sql = wrapperCaptor.getValue().getSqlSegment();
            assertTrue(sql.contains("status"), "wrapper 应包含 status 列条件");
            assertTrue(sql.contains("sort"), "wrapper 应包含 sort 排序");
        }
    }
}
