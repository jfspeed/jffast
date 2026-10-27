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
package cn.jfspeed.system.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.system.entity.SysDict;
import cn.jfspeed.system.mapper.SysDictMapper;

/**
 * 字典的三处作用域契约。
 *
 * <ul>
 *   <li><b>查重按「同父级 + 同类别」</b>：字典项归属于某个字典类型，不同类型下允许出现同名/同编码的
 *       字典项；不带作用域的查重会让「A 类型下的『启用』」把「B 类型下的『启用』」也判成重复。</li>
 *   <li><b>按编码取字典必须限定字典类型</b>：字典项的编码由名称拼音生成，与字典类型的编码相撞是常态；
 *       不限定 {@code is_type} 时 {@code selectOne} 撞码即抛 {@code TooManyResultsException} ⇒ 下拉接口 500。</li>
 *   <li><b>下拉项要带 value</b>：字典项的业务值就是 {@code value}，不下发的话下拉框拿不到选中值。</li>
 * </ul>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDictScopeAndLookupTest {

    @Mock
    private SysDictMapper baseMapper;

    @InjectMocks
    private SysDictService service;

    @BeforeAll
    static void initTableInfoCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDict.class);
    }

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    @SuppressWarnings("unchecked")
    private QueryWrapper<SysDict> captureCountWrapper() {
        ArgumentCaptor<QueryWrapper<SysDict>> captor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(baseMapper).selectCount(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<Wrapper<SysDict>> captureSelectWrappers() {
        ArgumentCaptor<Wrapper<SysDict>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper, atLeastOnce()).selectList(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("字典项查重必须限定父级，且把 is_type 为 NULL 的存量行一并纳入")
    void itemScopeIsParentAndKind() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.hasDictByNameAndCode("启用", null, null, 5L, 2);

        String sql = captureCountWrapper().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("parent_id"), "字典项查重必须限定父级，实际条件：" + sql);
        assertTrue(sql.contains("is_type <>"), "字典项查重不能把 is_type=1 的字典类型算进来，实际条件：" + sql);
        assertTrue(sql.contains("is_type is null"),
                "is_type 为 NULL 的存量字典项行也要纳入查重（SQL 里 <> 对 NULL 不成立），实际条件：" + sql);
    }

    @Test
    @DisplayName("字典类型查重限定 is_type = 1")
    void typeScopeIsDictTypeOnly() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.hasDictByNameAndCode("状态", null, null, 0L, 1);

        String sql = captureCountWrapper().getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("is_type ="), "字典类型查重必须限定 is_type = 1，实际条件：" + sql);
        assertFalse(sql.contains("is_type <>"), "字典类型查重不应包含字典项的条件，实际条件：" + sql);
    }

    @Test
    @DisplayName("不带作用域的查重保持全表口径（探测接口的既有语义不变）")
    void globalScopeKeepsLegacyBehaviour() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        service.hasDictByNameAndCode("启用", null, null);

        String sql = captureCountWrapper().getSqlSegment().toLowerCase(Locale.ROOT);
        assertFalse(sql.contains("is_type"), "全表口径不应带 is_type 条件，实际条件：" + sql);
        assertFalse(sql.contains("parent_id"), "全表口径不应带 parent_id 条件，实际条件：" + sql);
    }

    @Test
    @DisplayName("按编码取字典只认字典类型，且撞码不抛异常")
    void getByDictCodeFiltersTypeAndToleratesDuplicates() {
        SysDict type = new SysDict();
        type.setId(1L);
        type.setCode("qy");
        type.setIsType(1);
        SysDict duplicate = new SysDict();
        duplicate.setId(2L);
        duplicate.setCode("qy");
        duplicate.setIsType(1);
        when(baseMapper.selectList(any())).thenReturn(List.of(type, duplicate));

        SysDict found = service.getByDictCode("qy");

        assertNotNull(found, "应按编码取到字典类型");
        assertEquals(1L, found.getId(), "多条同编码的字典类型应取第一条而不是抛 TooManyResultsException");

        String sql = captureSelectWrappers().get(0).getSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("is_type ="), "按编码取字典必须限定字典类型，实际条件：" + sql);
    }

    @Test
    @DisplayName("字典项列表的查询列必须包含 value（下拉框要拿它当选中值）")
    void dictItemQuerySelectsValueColumn() {
        SysDict type = new SysDict();
        type.setId(1L);
        type.setCode("sys_status");
        type.setIsType(1);

        SysDict item = new SysDict();
        item.setId(2L);
        item.setParentId(1L);
        item.setCode("qy");
        item.setName("启用");
        item.setValue("1");
        item.setIsType(2);

        when(baseMapper.selectList(any())).thenAnswer(invocation -> {
            Wrapper<SysDict> wrapper = invocation.getArgument(0);
            String segment = wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
            return segment.contains("is_type =") ? List.of(type) : List.of(item);
        });

        try (MockedStatic<RedisUtil> redis = mockStatic(RedisUtil.class)) {
            redis.when(() -> RedisUtil.get(anyString())).thenReturn(null);

            List<SysDict> list = service.getListByDictCode("sys_status");

            assertEquals(1, list.size());
        }

        String selectSql = captureSelectWrappers().stream()
                .filter(LambdaQueryWrapper.class::isInstance)
                .map(wrapper -> ((LambdaQueryWrapper<SysDict>) wrapper).getSqlSelect())
                .findFirst()
                .orElse("");
        assertTrue(selectSql.toLowerCase(Locale.ROOT).contains("value"),
                "字典项查询列必须含 value，实际：" + selectSql);
    }
}
