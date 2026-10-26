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

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

import cn.jfspeed.common.core.annotation.IgnoreBaseField;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class BaseFieldInterceptor implements InnerInterceptor {

    private static class ModifyResult {
        final String sql;
        final List<Integer> removedIndices;

        ModifyResult(String sql, List<Integer> removedIndices) {
            this.sql = sql;
            this.removedIndices = removedIndices;
        }
    }

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+`?(\\w+)`?\\s*\\((.+?)\\)\\s*VALUES\\s*\\((.+?)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT_PATTERN = Pattern.compile(
            "SELECT\\s+(.+?)\\s+FROM",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
            "UPDATE\\s+`?(\\w+)`?\\s+SET\\s+(.+?)\\s+WHERE",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "ORDER\\s+BY\\s+(.+?)(?:LIMIT|$)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        String newSql = removeIgnoredColumns(mpBs.sql());
        if (newSql != null) {
            mpBs.sql(newSql);
        }
    }

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) {
        BoundSql boundSql = ms.getBoundSql(parameter);
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        String newSql = removeIgnoredColumns(mpBs.sql());
        if (newSql != null) {
            mpBs.sql(newSql);
        }
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        BoundSql boundSql = sh.getBoundSql();
        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        String sql = mpBs.sql();
        if (sql == null) return;

        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("INSERT")) {
            ModifyResult result = removeFromInsert(sql);
            if (result != null) {
                applyModifyResult(mpBs, result);
            }
        } else if (upperSql.startsWith("UPDATE")) {
            ModifyResult result = removeFromUpdate(sql);
            if (result != null) {
                applyModifyResult(mpBs, result);
            }
        } else {
            String newSql = removeIgnoredColumns(sql);
            if (newSql != null) {
                mpBs.sql(newSql);
            }
        }
    }

    private void applyModifyResult(PluginUtils.MPBoundSql mpBs, ModifyResult result) {
        mpBs.sql(result.sql);
        List<ParameterMapping> parameterMappings = mpBs.parameterMappings();
        if (parameterMappings != null && !result.removedIndices.isEmpty()) {
            List<ParameterMapping> newMappings = new ArrayList<>(parameterMappings);
            for (int i = result.removedIndices.size() - 1; i >= 0; i--) {
                newMappings.remove((int) result.removedIndices.get(i));
            }
            mpBs.parameterMappings(newMappings);
        }
    }

    private String removeIgnoredColumns(String sql) {
        if (sql == null) return null;

        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("INSERT")) {
            ModifyResult result = removeFromInsert(sql);
            return result != null ? result.sql : null;
        } else if (upperSql.startsWith("SELECT")) {
            boolean changed = false;
            String result = removeFromSelect(sql);
            if (result != null) {
                sql = result;
                changed = true;
            }
            result = removeFromOrderBy(sql);
            if (result != null) {
                sql = result;
                changed = true;
            }
            return changed ? sql : null;
        } else if (upperSql.startsWith("UPDATE")) {
            ModifyResult result = removeFromUpdate(sql);
            return result != null ? result.sql : null;
        }
        return null;
    }

    private ModifyResult removeFromInsert(String sql) {
        Matcher m = INSERT_PATTERN.matcher(sql);
        if (!m.find()) return null;

        String tableName = m.group(1);
        String columnsStr = m.group(2);
        String valuesStr = m.group(3);

        Set<String> ignored = getIgnoredColumnsByTable(tableName);
        if (ignored.isEmpty()) return null;

        List<String> columns = splitColumns(columnsStr);
        List<String> values = splitColumns(valuesStr);

        List<Integer> removeIndices = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            if (ignored.contains(columns.get(i).trim())) {
                removeIndices.add(i);
            }
        }
        if (removeIndices.isEmpty()) return null;

        for (int i = removeIndices.size() - 1; i >= 0; i--) {
            int idx = removeIndices.get(i);
            columns.remove(idx);
            values.remove(idx);
        }

        String newColumns = String.join(", ", columns);
        String newValues = String.join(", ", values);

        String newSql = sql.substring(0, m.start(2)) + newColumns + sql.substring(m.end(2), m.start(3))
                + newValues + sql.substring(m.end(3));
        return new ModifyResult(newSql, removeIndices);
    }

    private String removeFromSelect(String sql) {
        Matcher m = SELECT_PATTERN.matcher(sql);
        if (!m.find()) return null;

        String selectItems = m.group(1);

        String tableName = extractTableNameFromSelect(sql);
        if (tableName == null) return null;

        Set<String> ignored = getIgnoredColumnsByTable(tableName);
        if (ignored.isEmpty()) return null;

        List<String> items = splitColumns(selectItems);
        List<String> filtered = new ArrayList<>();
        boolean changed = false;
        for (String item : items) {
            String trimmed = item.trim();
            String colName = extractColumnName(trimmed);
            if (colName != null && ignored.contains(colName)) {
                changed = true;
                continue;
            }
            filtered.add(item);
        }
        if (!changed) return null;

        String newSelectItems = String.join(", ", filtered);
        return sql.substring(0, m.start(1)) + newSelectItems + sql.substring(m.end(1));
    }

    private ModifyResult removeFromUpdate(String sql) {
        Matcher m = UPDATE_PATTERN.matcher(sql);
        if (!m.find()) return null;

        String tableName = m.group(1);
        String setClause = m.group(2);

        Set<String> ignored = getIgnoredColumnsByTable(tableName);
        if (ignored.isEmpty()) return null;

        List<String> setItems = splitSetClause(setClause);
        List<String> filtered = new ArrayList<>();
        List<Integer> removeIndices = new ArrayList<>();
        for (int i = 0; i < setItems.size(); i++) {
            String item = setItems.get(i);
            String trimmed = item.trim();
            String colName = extractSetColumnName(trimmed);
            if (colName != null && ignored.contains(colName)) {
                removeIndices.add(i);
                continue;
            }
            filtered.add(item);
        }
        if (removeIndices.isEmpty()) return null;

        String newSetClause = String.join(", ", filtered);
        String newSql = sql.substring(0, m.start(2)) + newSetClause + sql.substring(m.end(2));
        return new ModifyResult(newSql, removeIndices);
    }

    private String removeFromOrderBy(String sql) {
        String tableName = extractTableNameFromSelect(sql);
        if (tableName == null) return null;

        Set<String> ignored = getIgnoredColumnsByTable(tableName);
        if (ignored.isEmpty()) return null;

        Matcher m = ORDER_BY_PATTERN.matcher(sql);
        if (!m.find()) return null;

        String orderByItems = m.group(1);
        List<String> items = splitColumns(orderByItems);
        List<String> filtered = new ArrayList<>();
        boolean changed = false;
        for (String item : items) {
            String trimmed = item.trim();
            String colName = extractOrderByColumnName(trimmed);
            if (colName != null && ignored.contains(colName)) {
                changed = true;
                continue;
            }
            filtered.add(item);
        }
        if (!changed) return null;

        if (filtered.isEmpty()) {
            return sql.substring(0, m.start()) + sql.substring(m.end(1));
        }

        String newOrderBy = String.join(", ", filtered);
        return sql.substring(0, m.start(1)) + newOrderBy + sql.substring(m.end(1));
    }

    private String extractOrderByColumnName(String orderByItem) {
        String trimmed = orderByItem.trim();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            trimmed = trimmed.substring(0, spaceIdx);
        }
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Set<String> getIgnoredColumnsByTable(String tableName) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
        if (tableInfo == null) return Collections.emptySet();

        Class<?> entityClass = tableInfo.getEntityType();
        if (entityClass == null) return Collections.emptySet();

        IgnoreBaseField annotation = entityClass.getAnnotation(IgnoreBaseField.class);
        if (annotation == null) return Collections.emptySet();

        Set<String> ignored = new HashSet<>();
        if (annotation.tenantId()) ignored.add("tenant_id");
        if (annotation.deptId()) ignored.add("dept_id");
        if (annotation.createBy()) ignored.add("create_by");
        if (annotation.createTime()) ignored.add("create_time");
        if (annotation.updateBy()) ignored.add("update_by");
        if (annotation.updateTime()) ignored.add("update_time");
        return ignored;
    }

    private List<String> splitColumns(String str) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private List<String> splitSetClause(String str) {
        return splitColumns(str);
    }

    private String extractColumnName(String selectItem) {
        String trimmed = selectItem.trim();
        int dotIdx = trimmed.lastIndexOf('.');
        if (dotIdx >= 0) {
            trimmed = trimmed.substring(dotIdx + 1);
        }
        int asIdx = -1;
        String upper = trimmed.toUpperCase();
        int idx = upper.indexOf(" AS ");
        if (idx >= 0) {
            trimmed = trimmed.substring(0, idx).trim();
        }
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private String extractSetColumnName(String setItem) {
        String trimmed = setItem.trim();
        int eqIdx = trimmed.indexOf('=');
        if (eqIdx < 0) return null;
        String colName = trimmed.substring(0, eqIdx).trim();
        if (colName.startsWith("`") && colName.endsWith("`")) {
            colName = colName.substring(1, colName.length() - 1);
        }
        return colName;
    }

    private String extractTableNameFromSelect(String sql) {
        Pattern p = Pattern.compile("FROM\\s+`?(\\w+)`?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(sql);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}