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

import cn.jfspeed.common.core.annotation.IgnoreBaseField;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.mybatis.config.DataScopeContext;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.parser.JsqlParserSupport;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.List;

/**
 * 数据权限自动注入拦截器
 * <p>
 * 对所有SQL查询自动追加数据权限过滤条件（WHERE dept_id = ? / FIND_IN_SET(...)等）。
 * 由 @DataPermission 注解 + DataPermissionAspect 触发，通过 ThreadLocal 传递过滤条件。
 * <p>
 * 使用方式：无需XML中手动加 ${params.dataScope}，标注 @DataPermission 后自动生效。
 * 同时支持 MyBatis-Plus 默认方法（selectList, selectPage 等）。
 *
 * @author jffast
 */
@Slf4j
public class DataScopeInnerInterceptor extends JsqlParserSupport implements InnerInterceptor {

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                           RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
        String dataScope = DataScopeContext.get();
        if (dataScope == null || dataScope.isEmpty()) {
            return;
        }

        PluginUtils.MPBoundSql mpBs = PluginUtils.mpBoundSql(boundSql);
        try {
            String sql = mpBs.sql();
            if (shouldSkipDataScope(sql)) {
                return;
            }
            mpBs.sql(parserSingle(sql, null));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DataScope interceptor failed, query rejected", e);
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "数据权限校验失败，已拒绝查询");
        }
    }

    private boolean shouldSkipDataScope(String sql) {
        try {
            net.sf.jsqlparser.statement.Statement stmt = CCJSqlParserUtil.parse(sql);
            String tableName = extractTableName(stmt);
            if (tableName != null) {
                TableInfo tableInfo = TableInfoHelper.getTableInfo(tableName);
                if (tableInfo != null) {
                    Class<?> entityClass = tableInfo.getEntityType();
                    if (entityClass != null) {
                        IgnoreBaseField annotation = entityClass.getAnnotation(IgnoreBaseField.class);
                        if (annotation != null && annotation.deptId()) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to check data scope skip: {}", e.getMessage());
        }
        return false;
    }

    private String extractTableName(net.sf.jsqlparser.statement.Statement statement) {
        if (statement instanceof Select) {
            PlainSelect plainSelect = ((Select) statement).getPlainSelect();
            if (plainSelect != null) {
                FromItem fromItem = plainSelect.getFromItem();
                if (fromItem instanceof Table) {
                    return ((Table) fromItem).getName();
                }
            }
        }
        return null;
    }

    @Override
    protected void processSelect(Select select, int index, String sql, Object obj) {
        if (select.getSelectBody() instanceof PlainSelect) {
            processPlainSelect((PlainSelect) select.getSelectBody());
        } else if (select.getSelectBody() instanceof SetOperationList) {
            SetOperationList setOpList = (SetOperationList) select.getSelectBody();
            for (Select body : setOpList.getSelects()) {
                if (body instanceof PlainSelect) {
                    processPlainSelect((PlainSelect) body);
                }
            }
        }
    }

    private void processPlainSelect(PlainSelect plainSelect) {
        String dataScope = DataScopeContext.get();
        if (dataScope == null || dataScope.isEmpty()) {
            return;
        }

        try {
            Expression condition = CCJSqlParserUtil.parseCondExpression(dataScope);
            Expression where = plainSelect.getWhere();
            if (where == null) {
                plainSelect.setWhere(condition);
            } else {
                plainSelect.setWhere(new AndExpression(where, condition));
            }
        } catch (Exception e) {
            log.error("Failed to inject dataScope condition, query rejected", e);
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "数据权限校验失败，已拒绝查询");
        }
    }
}
