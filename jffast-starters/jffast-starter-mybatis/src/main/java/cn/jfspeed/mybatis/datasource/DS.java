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
package cn.jfspeed.mybatis.datasource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 动态数据源切换注解（仅在 jffast.datasource.dynamic.enabled=true 时生效）
 * <p>
 * 标在类或方法上，方法级优先于类级；不标注则使用 primary 数据源：
 * <pre>
 * &#064;DS("slave1")
 * public List&lt;SysUser&gt; listFromSlave() { ... }
 * </pre>
 *
 * @author jffast
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DS {

    /**
     * 数据源名称（jffast.datasource.dynamic.datasource 下的 key），空串表示 primary
     */
    String value() default "";
}
