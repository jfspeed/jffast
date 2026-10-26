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
package cn.jfspeed.demo.service;

import java.util.List;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.demo.domain.dto.DemoExampleExcel;
import cn.jfspeed.demo.domain.dto.DemoExampleQueryRequest;
import cn.jfspeed.demo.entity.DemoExample;
import cn.jfspeed.demo.mapper.DemoExampleMapper;

/**
 * Service 层单元测试<b>参考模板</b>：新增模块时复制本类改名即可，覆盖最常见的四类断言。
 * <p>
 * <b>写 Service 测试前必读的三件事</b>（都是真实踩过的坑）：
 * <ol>
 *   <li><b>LambdaQueryWrapper 需要实体元数据</b>：`DemoExample::getTitle` 这类 lambda 在纯
 *       Mockito 环境下解析列名会 NPE，必须先 {@code TableInfoHelper.initTableInfo}（见
 *       {@link #initTableInfo}）；漏掉这步的报错是 getSqlSegment NPE，与业务无关，极易误判</li>
 *   <li><b>selectPage 是「参数填充」不是「返回值」</b>：MyBatis-Plus 的分页结果装在入参
 *       {@code Page} 对象里，mock 时用 thenAnswer 改写入参再原样返回</li>
 *   <li><b>saveBatch 走批量 SqlSession，纯 mock 拦不到</b>：对 Service 手动构造 spy（见 setUp），
 *       stub 双参重载 {@code saveBatch(list, batchSize)}；注意它返回 boolean 而非 void，
 *       用 {@code doReturn(true)} 而不是 {@code doNothing}</li>
 *   <li><b>@Spy + @InjectMocks 不会注入 baseMapper</b>：spy 包装实例不走 Mockito 字段注入，
 *       报错是 "baseMapper can not be null"；需手动 {@code ReflectionTestUtils.setField}（见 setUp）</li>
 * </ol>
 * 测试重点顺序：<b>业务分支 &gt; 落库内容（captor）&gt; 查询条件（wrapper）&gt; 返回值</b>。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemoExampleServiceTest {

    @Mock
    private DemoExampleMapper demoExampleMapper;

    private DemoExampleService service;

    @BeforeEach
    void setUp() {
        // 坑：@Spy + @InjectMocks 组合不会向 ServiceImpl.baseMapper 注入 mock（baseMapper can not be null）。
        // 正确姿势：手动 new + spy，再用 ReflectionTestUtils 注入 protected 字段。
        service = spy(new DemoExampleService());
        ReflectionTestUtils.setField(service, "baseMapper", demoExampleMapper);
    }

    @BeforeAll
    static void initTableInfo() {
        // 让 LambdaQueryWrapper 能把 DemoExample::getTitle 解析成列名；每个被测实体都要注册一次
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), DemoExample.class);
    }

    @Nested
    @DisplayName("importDemo 导入")
    class ImportDemo {

        @Test
        @DisplayName("空列表直接短路，不触发批量保存")
        void skipsWhenListEmpty() {
            service.importDemo(List.of());
            service.importDemo(null);

            verify(service, never()).saveBatch(anyList(), anyInt());
        }

        @Test
        @DisplayName("Excel 行逐条映射，并补默认值 isDeleted=0 后批量落库")
        void mapsExcelRowsAndFillsDefaults() {
            // 两个坑：① saveBatch 最终走批量 SqlSession，纯 Mockito 环境拦不到，
            // 必须 stub spy 的具体实现方法；② 它返回 boolean 而非 void，用 doReturn 而不是 doNothing
            doReturn(true).when(service).saveBatch(anyList(), anyInt());

            DemoExampleExcel excel = new DemoExampleExcel();
            excel.setTitle("行一");
            excel.setStatus(1);

            service.importDemo(List.of(excel));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<DemoExample>> captor = ArgumentCaptor.forClass(List.class);
            verify(service).saveBatch(captor.capture(), anyInt());

            List<DemoExample> saved = captor.getValue();
            assertEquals(1, saved.size());
            assertEquals("行一", saved.get(0).getTitle());
            assertEquals(0, saved.get(0).getIsDeleted());
        }
    }

    @Nested
    @DisplayName("page 分页查询")
    class PageQuery {

        @Test
        @DisplayName("分页结果来自入参 Page 的填充，并转换为 PageRes")
        void mapsPageResult() {
            // selectPage 是「参数填充」语义：结果装在入参 Page 里，mock 时填充再返回
            when(demoExampleMapper.selectPage(any(Page.class), any())).thenAnswer(inv -> {
                Page<DemoExample> page = inv.getArgument(0);
                page.setRecords(List.of(new DemoExample()));
                page.setTotal(1);
                return page;
            });

            // PageReq.getPage() 负责构建 MP 的 Page 对象，无需手动设分页参数
            PageRes<DemoExample> res = service.page(new DemoExampleQueryRequest());

            assertEquals(1, res.getTotal());
            assertFalse(res.getList().isEmpty());
        }
    }

    @Nested
    @DisplayName("updateStatus 局部更新")
    class UpdateStatus {

        @Test
        @DisplayName("只更新状态列；mapper 返回影响行数决定结果")
        void updatesStatusOnly() {
            when(demoExampleMapper.update(any(), any())).thenReturn(1);
            assertTrue(service.updateStatus(1L, 0));

            when(demoExampleMapper.update(any(), any())).thenReturn(0);
            assertFalse(service.updateStatus(1L, 0));
        }
    }
}
