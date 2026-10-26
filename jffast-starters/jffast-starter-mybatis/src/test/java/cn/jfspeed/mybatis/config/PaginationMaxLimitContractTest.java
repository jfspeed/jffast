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
package cn.jfspeed.mybatis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 分页封顶契约：MyBatis-Plus 的 {@code maxLimit} 默认是 null（不限单页条数），
 * 任意列表接口传 {@code pageSize=1000000} 就能一次把全表读进内存。框架必须默认夹住，
 * 并把上限做成可配项——此断言防止将来重排拦截器链时把 setMaxLimit 丢掉。
 *
 * @author jffast
 */
class PaginationMaxLimitContractTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JfFastMybatisAutoConfiguration.class));

    private static long maxLimitOf(MybatisPlusInterceptor interceptor) {
        List<InnerInterceptor> chain = interceptor.getInterceptors();
        PaginationInnerInterceptor pagination = chain.stream()
                .filter(PaginationInnerInterceptor.class::isInstance)
                .map(PaginationInnerInterceptor.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("拦截器链里没有 PaginationInnerInterceptor，分页已失效"));
        Long maxLimit = pagination.getMaxLimit();
        assertNotNull(maxLimit, "PaginationInnerInterceptor.maxLimit 不能为 null（null = 单页不限条数）");
        assertTrue(maxLimit > 0, "单页封顶必须为正数，实际：" + maxLimit);
        return maxLimit;
    }

    @Test
    @DisplayName("未显式配置时单页封顶取默认 500")
    void maxLimitDefaultsToBounds() {
        runner.run(context -> assertEquals(500L, maxLimitOf(context.getBean(MybatisPlusInterceptor.class)),
                "默认封顶应为 500：前端最大档位 100，留出批量余量，同时挡住 pageSize=1000000 读全表"));
    }

    @Test
    @DisplayName("上限由 jffast.mybatis.page-max-limit 显式配置")
    void maxLimitIsConfigurable() {
        runner.withPropertyValues("jffast.mybatis.page-max-limit=1200")
                .run(context -> assertEquals(1200L, maxLimitOf(context.getBean(MybatisPlusInterceptor.class)),
                        "部署方调高/调低封顶应生效"));
    }
}
