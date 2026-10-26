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
package cn.jfspeed.mybatis.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.mybatis.config.DataScopeContext;

/**
 * 数据权限片段的注入形状：每个可注入分支都必须带上条件。
 * <p>
 * 这里盯的是"什么都不做"那一类分支。旧实现只认 {@code PlainSelect} 与顶层
 * {@code SetOperationList}，括号包起来的 UNION 分支（解析树里是 {@code ParenthesedSelect}）
 * 会原样放行——不是报错，而是<b>安静地返回一条完全不过滤的 SQL</b>，
 * 表现是"某个部门的人通过列表页看到了别的部门的数据， yet 权限配置一切正常"。
 * <p>
 * 断言一律数条件的出现次数：只断"包含"的话，一条两支的 SQL 只注入一支也能过。
 *
 * @author jffast
 */
class DataScopeInjectionShapeTest {

    private static final String SCOPE = "dept_id = 7";
    private static final String MARKER = "dept_id = 7";

    private final DataScopeInnerInterceptor interceptor = new DataScopeInnerInterceptor();

    @AfterEach
    void clearScope() {
        DataScopeContext.clear();
    }

    private String rewrite(String sql) {
        DataScopeContext.set(SCOPE);
        return interceptor.parserSingle(sql, null);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    @Test
    @DisplayName("普通查询：条件与原有 WHERE 相与")
    void plainSelectIsFiltered() {
        String sql = rewrite("select id from sys_user where status = 1");

        assertTrue(sql.contains(MARKER), "未注入数据权限：" + sql);
        assertTrue(sql.toLowerCase().contains(" and "), "条件应与原 WHERE 相与，实际：" + sql);
    }

    @Test
    @DisplayName("括号包起来的 UNION：两支都必须过滤（旧实现只认 PlainSelect，另一支裸奔）")
    void parenthesedUnionBranchesAreBothFiltered() {
        String sql = rewrite("(select id from sys_user) union (select id from sys_user_draft)");

        assertEquals(2, countOccurrences(sql, MARKER), "两支 UNION 都应带数据权限，实际：" + sql);
    }

    @Test
    @DisplayName("不括号的 UNION 同样两支都过滤")
    void plainUnionBranchesAreBothFiltered() {
        String sql = rewrite("select id from sys_user union select id from sys_user_draft");

        assertEquals(2, countOccurrences(sql, MARKER), "实际：" + sql);
    }

    @Test
    @DisplayName("最外层就是括号查询时也要下钻注入")
    void topParenthesedSelectIsFiltered() {
        String sql = rewrite("(select id from sys_user where status = 1)");

        assertEquals(1, countOccurrences(sql, MARKER), "实际：" + sql);
    }

    @Test
    @DisplayName("认不出的查询形状直接拒绝，不得静默放行未过滤 SQL")
    void unknownShapeFailsClosed() {
        DataScopeContext.set(SCOPE);

        // VALUES (...) 在 JSqlParser 5.2 里也是 Select 的子类，但没有可注入的 WHERE
        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.parserSingle("values (1, 2)", null));

        assertTrue(ex.getMessage().contains("数据权限"), "实际提示：" + ex.getMessage());
    }

    @Test
    @DisplayName("没有数据权限上下文时不改写 SQL")
    void noScopeLeavesSqlUntouched() {
        String sql = interceptor.parserSingle("select id from sys_user", null);

        assertTrue(!sql.contains(MARKER), "无上下文却注入了条件：" + sql);
    }
}
