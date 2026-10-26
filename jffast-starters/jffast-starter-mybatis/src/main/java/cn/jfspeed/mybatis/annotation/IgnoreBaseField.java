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
package cn.jfspeed.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体类中哪些 BaseEntity 字段在数据库表中不存在。
 * 标注后，自动填充、租户拦截、数据权限拦截、SQL生成均会跳过这些字段。
 *
 * <pre>
 * &#64;IgnoreBaseField(deptId = true)
 * public class LearnSubjects extends BaseEntity { ... }
 * </pre>
 *
 * @author jffast
 * @deprecated 已迁移到 {@link cn.jfspeed.common.core.annotation.IgnoreBaseField}，
 * 本注解仅保留以兼容既有生成代码，后续 minor 版本删除。
 */
@Deprecated(forRemoval = true)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreBaseField {

    boolean tenantId() default false;

    boolean deptId() default false;

    boolean createBy() default false;

    boolean createTime() default false;

    boolean updateBy() default false;

    boolean updateTime() default false;
}