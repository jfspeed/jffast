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
package cn.jfspeed;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * swagger 注解构型回归测试（无需 MySQL / Redis）。
 *
 * <p>背景：springdoc 2.x（knife4j 时代）对应非 jakarta 构型的
 * {@code io.swagger.core.v3:swagger-annotations}；升级 springdoc 3.x 后传递引入
 * {@code swagger-*-jakarta}。两种构型的注解包名完全相同
 * （{@code io.swagger.v3.oas.annotations}），一旦旧构型仍在依赖里，
 * fat jar 中按文件名排序它会先于 jakarta 版被加载——旧版缺
 * {@code Schema.$dynamicRef()}（2.2.30 才加入），springdoc 3 调用即
 * {@code NoSuchMethodError}，症状是 {@code /v3/api-docs} 恒 500、
 * Swagger UI 打不开接口定义。
 *
 * <p>两条断言分工：类上是否有 {@code $dynamicRef}（版本线）＋ 胜出 jar 是否为
 * jakarta 构型（构型线，旧构型在 fat jar 里必然胜出，此处提前拦截）。
 *
 * @author jffast
 */
class SwaggerAnnotationsFlavorTest {

    @Test
    void winnerMustBeJakartaFlavorWithDynamicRef() {
        assertDoesNotThrow(() -> Schema.class.getMethod("$dynamicRef"),
                "当前 classpath 胜出的 swagger Schema 版本低于 2.2.30（无 $dynamicRef()），"
                        + "springdoc 3.x 调用会 NoSuchMethodError 使 /v3/api-docs 恒 500");

        String jar = Schema.class.getProtectionDomain().getCodeSource().getLocation().toString();
        assertTrue(jar.contains("swagger-annotations-jakarta"),
                "classpath 胜出的是非 jakarta 构型的 swagger-annotations（" + jar + "）。"
                        + "修法：jffast-bom 统一管理 swagger-annotations-jakarta，"
                        + "版本与 springdoc 3.x 传递引入的 swagger-core-jakarta 对齐。");
    }
}
