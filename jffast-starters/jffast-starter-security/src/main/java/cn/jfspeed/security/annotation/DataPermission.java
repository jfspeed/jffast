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
package cn.jfspeed.security.annotation;

import java.lang.annotation.*;

/**
 * 数据权限注解 - 自动注入数据范围过滤条件
 * <p>
 * 标注在Service方法上，AOP切面会自动将当前用户的数据权限转换为SQL过滤条件，
 * 注入到方法的Map参数中。适用于任何继承了BaseService的业务Service。
 * <p>
 * 使用方式：
 * <pre>
 * // 按部门过滤
 * {@code @DataPermission(deptField = "d.id")}
 * public PageRes<Order> page(Map<String, Object> params, PageReq pageReq) { ... }
 *
 * // 按部门和用户过滤
 * {@code @DataPermission(deptField = "d.id", userField = "u.id")}
 * public PageRes<User> page(Map<String, Object> params) { ... }
 * </pre>
 *
 * @author jffast
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {

    /**
     * 部门ID字段（SQL中的列别名，如 "d.id" 表示 dept表的id列）
     */
    String deptField() default "";

    /**
     * 用户ID字段（SQL中的列别名，如 "u.id" 表示 user表的id列）
     */
    String userField() default "";

    /**
     * 是否使用ID进行过滤，默认true。
     * <p>{@code useId=false} 时按部门名称拼接，仅适用于名称来自可信配置的场景；
     * 名称中的反斜杠与单引号会被转义，但任意名称仍不应直接暴露给终端用户填写。</p>
     */
    boolean useId() default true;
}
