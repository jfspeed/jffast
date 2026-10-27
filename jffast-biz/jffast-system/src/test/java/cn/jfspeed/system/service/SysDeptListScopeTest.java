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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import cn.jfspeed.security.annotation.DataPermission;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.mapper.SysDeptMapper;

/**
 * P3-4：部门按名称检索回填祖先时，无法看到数据范围外的部门。
 *
 * <p>原实现用无条件全表查询建索引，于是「范围内的子部门」会沿父链把范围外祖先的
 * id / 名称回填进结果。现在索引本身由带 {@code @DataPermission} 的独立 Bean 查询，
 * 范围外父级取不到即停。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDeptListScopeTest {

    @Mock
    private SysDeptMapper baseMapper;
    @Mock
    private SysDeptScopeQuery sysDeptScopeQuery;

    @InjectMocks
    private SysDeptService service;

    @BeforeEach
    void setUp() {
        // @InjectMocks 不注入父类继承字段，手动注入 ServiceImpl.baseMapper
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private SysDept dept(Long id, Long parentId, String name, int level) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        dept.setLevel(level);
        dept.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(id));
        return dept;
    }

    private Map<String, Object> searchByName() {
        Map<String, Object> map = new HashMap<>();
        map.put("deptName", "研发");
        return map;
    }

    @Test
    @DisplayName("索引查询必须走带 @DataPermission 的独立 Bean")
    void indexQueryIsScopeConstrained() throws NoSuchMethodException {
        DataPermission annotation = SysDeptScopeQuery.class
                .getMethod("listVisibleForIndex").getAnnotation(DataPermission.class);

        assertNotNull(annotation, "部门索引查询必须标注 @DataPermission，否则会全表可见");
        assertEquals("id", annotation.deptField());
    }

    @Test
    @DisplayName("可见祖先：正常回填并在结果中")
    void visibleAncestorIsAppended() {
        SysDept child = dept(11L, 10L, "研发一组", 2);
        SysDept parent = dept(10L, 0L, "研发中心", 1);
        when(baseMapper.list(any())).thenReturn(new ArrayList<>(List.of(child)));
        when(sysDeptScopeQuery.listVisibleForIndex()).thenReturn(List.of(child, parent));

        List<SysDept> result = service.list(searchByName());

        verify(sysDeptScopeQuery).listVisibleForIndex();
        Set<Long> ids = result.stream().map(SysDept::getId).collect(Collectors.toSet());
        assertTrue(ids.contains(11L));
        assertTrue(ids.contains(10L), "可见祖先应被回填");
    }

    @Test
    @DisplayName("范围外祖先：索引里取不到，不得出现在结果中")
    void outOfScopeAncestorIsNotLeaked() {
        SysDept child = dept(11L, 10L, "研发一组", 2);
        when(baseMapper.list(any())).thenReturn(new ArrayList<>(List.of(child)));
        // 数据权限过滤后只剩可见的自身，父级 10 不在范围内
        when(sysDeptScopeQuery.listVisibleForIndex()).thenReturn(List.of(child));

        List<SysDept> result = service.list(searchByName());

        Set<Long> ids = result.stream().map(SysDept::getId).collect(Collectors.toSet());
        assertEquals(Set.of(11L), ids, "范围外祖先的 id 不能出现在结果里");
        assertTrue(result.stream().noneMatch(item -> item.getDeptName() != null
                && item.getDeptName().contains("研发中心")), "范围外祖先的名称不能出现");
    }
}
