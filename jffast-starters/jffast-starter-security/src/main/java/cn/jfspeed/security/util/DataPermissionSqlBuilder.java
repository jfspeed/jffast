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
package cn.jfspeed.security.util;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.security.annotation.DataPermission;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 数据权限 SQL 片段构建器。
 * <p>切面与异步任务快照共用同一实现：异步线程没有登录态，只能在提交时按注解元数据
 * 生成片段并保存，工作线程恢复该片段，保证异步导出的行集与同步调用一致。</p>
 *
 * @author jffast
 */
public final class DataPermissionSqlBuilder {

    private DataPermissionSqlBuilder() {
    }

    /**
     * 按注解元数据构建数据权限片段
     */
    public static String build(LoginUser user, DataPermission dataPermission) {
        if (dataPermission == null) {
            return null;
        }
        return build(user, dataPermission.deptField(), dataPermission.userField(), dataPermission.useId());
    }

    /**
     * 按字段配置构建数据权限片段
     */
    public static String build(LoginUser user, String deptField, String userField, boolean useId) {
        if (user == null) {
            return null;
        }
        Map<String, Object> dataScope = user.getDataScope();
        if (dataScope == null || dataScope.isEmpty()) {
            return null;
        }
        // 受限用户无任何可用范围：必须拒绝查询，不能被其它键稀释成全量
        if (dataScope.containsKey(BaseConstant.DATA_SCOPE_NONE)) {
            return "1 = 0";
        }

        StringBuilder sql = new StringBuilder();
        if (StrUtil.isNotBlank(deptField)) {
            String safeField = sanitizeFieldName(deptField);
            if (useId) {
                appendDeptIdCondition(sql, safeField, dataScope);
            } else {
                appendDeptNameCondition(sql, safeField, dataScope);
            }
        }
        if (StrUtil.isNotBlank(userField)) {
            Object self = dataScope.get(BaseConstant.DATA_SCOPE_SELF);
            if (self != null) {
                if (sql.length() > 0) {
                    sql.append(" and ");
                }
                sql.append(String.format("%s = %s", sanitizeFieldName(userField), self));
            }
        }
        // 非空 map 却拼不出任何条件 ⇒ 受限用户无可用范围，fail-closed
        return sql.length() > 0 ? sql.toString() : "1 = 0";
    }

    /**
     * 按目标方法的 {@link DataPermission} 注解元数据构建当前登录用户的片段。
     * <p>注解缺失或方法不存在属于内部契约破坏：直接失败，调用方据此在创建任务前中止，
     * 不得回退成「无数据权限」的全量查询。</p>
     */
    public static String resolve(Class<?> target, String method, Class<?>... parameterTypes) {
        Method resolved = ReflectionUtils.findMethod(target, method, parameterTypes);
        if (resolved == null) {
            throw new IllegalStateException("数据权限方法不存在: " + target.getName() + "#" + method);
        }
        DataPermission annotation = resolved.getAnnotation(DataPermission.class);
        if (annotation == null) {
            throw new IllegalStateException("数据权限注解缺失: " + target.getName() + "#" + method);
        }
        return build(LoginUtil.getLoginUser(), annotation);
    }

    private static void appendDeptIdCondition(StringBuilder sql, String deptField, Map<String, Object> dataScope) {
        Object customId = dataScope.get(BaseConstant.DATA_SCOPE_CUSTOM_ID);
        Object parentDeptId = dataScope.get(BaseConstant.DATA_SCOPE_DEPT_PARENT_ID);
        Object deptId = dataScope.get(BaseConstant.DATA_SCOPE_DEPT_ID);

        if (customId != null) {
            sql.append(String.format("(FIND_IN_SET(%s,'%s')", deptField, sanitizeIdValue(customId)));
            if (parentDeptId != null) {
                sql.append(String.format(" or FIND_IN_SET(%s,'%s')", deptField, sanitizeIdValue(parentDeptId)));
            } else if (deptId != null) {
                sql.append(String.format(" or %s = '%s'", deptField, sanitizeIdValue(deptId)));
            }
            sql.append(")");
        } else if (parentDeptId != null) {
            sql.append(String.format("FIND_IN_SET(%s,'%s')", deptField, sanitizeIdValue(parentDeptId)));
        } else if (deptId != null) {
            sql.append(String.format("%s = '%s'", deptField, sanitizeIdValue(deptId)));
        }
    }

    private static void appendDeptNameCondition(StringBuilder sql, String deptField, Map<String, Object> dataScope) {
        Object customName = dataScope.get(BaseConstant.DATA_SCOPE_CUSTOM_NAME);
        Object parentDeptName = dataScope.get(BaseConstant.DATA_SCOPE_DEPT_PARENT_NAME);
        Object deptName = dataScope.get(BaseConstant.DATA_SCOPE_DEPT_NAME);

        if (customName != null) {
            sql.append(String.format("(FIND_IN_SET(%s,'%s')", deptField, sanitizeNameValue(customName)));
            if (parentDeptName != null) {
                sql.append(String.format(" or FIND_IN_SET(%s,'%s')", deptField, sanitizeNameValue(parentDeptName)));
            } else if (deptName != null) {
                sql.append(String.format(" or %s = '%s'", deptField, sanitizeNameValue(deptName)));
            }
            sql.append(")");
        } else if (parentDeptName != null) {
            sql.append(String.format("FIND_IN_SET(%s,'%s')", deptField, sanitizeNameValue(parentDeptName)));
        } else if (deptName != null) {
            sql.append(String.format("%s = '%s'", deptField, sanitizeNameValue(deptName)));
        }
    }

    private static String sanitizeFieldName(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        return fieldName.replaceAll("[^a-zA-Z0-9_.]", "");
    }

    private static String sanitizeIdValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().replaceAll("[^0-9,]", "");
    }

    /**
     * 部门名称仅用于 {@code useId=false} 的可信配置；反斜杠必须先于单引号转义，
     * 否则 MySQL 会把 {@code \'} 解释为转义引号，吞掉闭合引号。
     */
    private static String sanitizeNameValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().replace("\\", "\\\\").replace("'", "''");
    }
}
