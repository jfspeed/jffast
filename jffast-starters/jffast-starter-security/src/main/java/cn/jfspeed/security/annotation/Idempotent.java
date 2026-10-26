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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口幂等性注解
 * <p>
 * 基于 Redis 实现，防止重复提交。使用方式：
 * <pre>
 * &#064;Idempotent(expire = 5)  // 5 秒内幂等
 * &#064;PostMapping("/save")
 * public R save(&#064;RequestBody SysUser user) { ... }
 * </pre>
 * 幂等 key 生成规则：userId + URI + 请求参数 hash
 *
 * @author jffast
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 幂等有效期（秒），默认 5 秒
     */
    int expire() default 5;

    /**
     * 提示信息
     */
    String message() default "请勿重复提交";
}
