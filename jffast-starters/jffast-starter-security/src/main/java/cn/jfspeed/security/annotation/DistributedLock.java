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
 * 分布式锁注解
 *
 * @author jffast
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁的key，支持SpEL表达*
     * 例如: "order:#{#orderId}"
     */
    String key();

    /**
     * 锁过期时间（毫秒），默认30*
     */
    long expireTime() default 30000;

    /**
     * 获取锁失败时的提示信*
     */
    String message() default "系统繁忙，请稍后再试";
}