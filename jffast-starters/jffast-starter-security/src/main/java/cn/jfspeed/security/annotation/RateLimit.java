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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流。
 * <p>
 * <b>两套取值口径，别混</b>：
 * <ol>
 *   <li>{@code maxCount} / {@code seconds} 都留默认（{@code 0}）⇒ 走 <b>yml 注册表实例</b>
 *       （`resilience4j.ratelimiter.instances.<value>`），运维可在 yml 里调、不用改代码。
 *       全仓绝大多数用法走这条路。</li>
 *   <li>显式给了 {@code maxCount} 与 {@code seconds}（都 &gt; 0）⇒ 按注解值**就地建实例**
 *       （键为 {@code value:maxCount:seconds}），yml 同名实例对该方法不再生效。</li>
 * </ol>
 * 之所以默认留 0 而不是给「10 次 / 60 秒」这类硬默认值：历史上这三个参数**切面根本没读**（死参数），
 * 一旦让默认值直接生效，全仓 44 处 `@RateLimit("default")` 会从 yml 的 100 次/秒骤降到
 * 10 次/60 秒 —— 属**静默收紧**，正常操作会被 429。因此默认值取「未指定」，让显式传值才生效。
 *
 * @author jffast
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流规则名：对应 `resilience4j.ratelimiter.instances.<value>`（未显式给阈值时） */
    String value() default "default";

    /** 窗口内允许的请求数；{@code <=0} 表示不指定，走 yml 实例 */
    int maxCount() default 0;

    /** 窗口长度（秒）；{@code <=0} 表示不指定，走 yml 实例 */
    int seconds() default 0;

    /** 被限流时的提示文案 */
    String message() default "请求过于频繁，请稍后再试";

    /** 被限流时的响应码 */
    int fallbackResponseCode() default 429;
}