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

import cn.jfspeed.system.domain.dto.SysDictQueryRequest;
import cn.jfspeed.system.entity.SysDict;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典 {@code is_type} 的空值契约（无需 MySQL）。
 *
 * <p>{@code sys_dict.is_type} 的 DDL 是 {@code tinyint(1) DEFAULT NULL}，即该列<b>允许 NULL</b>。
 * 而 {@code is_type} 在业务里是「1=字典类型，2=字典项」的判别列，任何一处按 {@code == 1}
 * 直接拆箱 Integer，遇到 NULL 行就是 NPE。
 *
 * <p>两个方向都要钉住：
 * <ul>
 *   <li><b>取值方向</b>：{@link SysDict#isDictType(SysDict)} 对 NULL 的 {@code is_type} 与
 *       NULL 实体都必须返回 false 而不是抛 NPE——字典树/列表/缓存清理都走它。</li>
 *   <li><b>可见方向</b>：字典项列表的过滤条件不能把 {@code is_type IS NULL} 的行整条滤掉。
 *       SQL 的 {@code is_type <> 1} 对 NULL 不成立（NULL 比较结果是 UNKNOWN），一旦漏成这种写法，
 *       存量空值行在管理页里<b>彻底看不见</b>：看不见就改不了。</li>
 * </ul>
 *
 * @author jffast
 */
class SysDictIsTypeNullSafetyTest {

    /** 与 {@code is_type <> ? OR is_type IS NULL} 同形，允许中间出现任意空白与占位符。 */
    private static final Pattern NULL_INCLUSIVE_FILTER =
            Pattern.compile("is_type\\s*<>\\s*.{0,60}?or\\s+is_type\\s+is\\s+null");

    private static SysDict dict(Integer isType) {
        SysDict dict = new SysDict();
        dict.setIsType(isType);
        return dict;
    }

    /**
     * LambdaQueryWrapper 在渲染 SQL 时要把 {@code SysDict::getIsType} 这样的方法引用
     * 解析成列名，依赖 MyBatis-Plus 的 TableInfo 缓存；该缓存在 Spring 启动时由
     * {@code TableInfoHelper} 填好，纯单测环境里没有，于是渲染时抛
     * {@code can not find lambda cache for this entity}。这里手工初始化一次。
     */
    @BeforeAll
    static void initTableInfoCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysDict.class);
    }

    @Test
    @DisplayName("isDictType 对 is_type 为 NULL 的字典必须返回 false 而不是抛 NPE")
    void nullIsTypeIsNotDictType() {
        assertFalse(SysDict.isDictType(dict(null)),
                "is_type 为 NULL 的历史行不是字典类型；应返回 false，实际抛了异常或返回 true");
    }

    @Test
    @DisplayName("isDictType 对 NULL 实体必须返回 false 而不是抛 NPE")
    void nullEntityIsNotDictType() {
        assertFalse(SysDict.isDictType(null),
                "按 id 查不到字典时实体为 NULL；应返回 false，实际抛了异常或返回 true");
    }

    @Test
    @DisplayName("isDictType 仍按 is_type=1 判别字典类型，=2 判别字典项")
    void stillDiscriminatesByValue() {
        assertTrue(SysDict.isDictType(dict(1)), "is_type=1 是字典类型");
        assertFalse(SysDict.isDictType(dict(2)), "is_type=2 是字典项，不是字典类型");
    }

    @Test
    @DisplayName("字典项列表必须把 is_type IS NULL 的行纳入（否则存量空值行在管理页里消失）")
    void dictItemQueryMustIncludeNullIsTypeRows() {
        String sql = SysDictService.buildDictItemQuery(new SysDictQueryRequest())
                .getSqlSegment()
                .toLowerCase(Locale.ROOT);
        assertTrue(NULL_INCLUSIVE_FILTER.matcher(sql).find(),
                "字典项过滤条件必须是 (is_type <> 1 OR is_type IS NULL)："
                        + "只写 is_type <> 1 会把 NULL 行整条滤掉（NULL 比较结果是 UNKNOWN），"
                        + "那些行在字典管理页里就再也看不见了。实际 SQL 片段：" + sql);
    }
}
