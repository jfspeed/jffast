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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import cn.jfspeed.mybatis.interceptor.DataScopeInnerInterceptor;

/**
 * 数据权限拦截器的装配缺省值：漏配必须仍然装配。
 * <p>
 * {@code @ConditionalOnProperty(..., matchIfMissing = false)} 时，漏写
 * {@code jffast.mybatis.data-scope.enabled} 只是"没有这个 bean"，不报错也不告警，
 * 而 {@code @DataPermission} 切面照样把片段塞进 {@link DataScopeContext} —— 于是注解看起来生效了、
 * 上下文里有值、SQL 却一条都没被改写过，表现是"给了部门权限的人仍能看到全表"。
 * Base 只在单体 application.yml 写了这一行，cloud 的三个服务都没写，切到 cloud 形态即整体静默失效。
 * 安全开关的缺省只能是"开着"。
 *
 * @author jffast
 */
class DataScopeInterceptorWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JfFastMybatisAutoConfiguration.class));

    @Test
    @DisplayName("不写 jffast.mybatis.data-scope.enabled 也装配数据权限拦截器")
    void wiredWithoutExplicitProperty() {
        runner.run(context -> {
            assertNotNull(context.getBean(DataScopeInnerInterceptor.class),
                    "漏配开关就不装配，等于数据权限静默失效");
            List<InnerInterceptor> chain = context.getBean(MybatisPlusInterceptor.class).getInterceptors();
            assertTrue(chain.stream().anyMatch(DataScopeInnerInterceptor.class::isInstance),
                    "拦截器链里没有 DataScopeInnerInterceptor，实际链：" + chain);
        });
    }

    @Test
    @DisplayName("显式 false 才关掉（保留部署方的关闭手段）")
    void explicitFalseDisables() {
        runner.withPropertyValues("jffast.mybatis.data-scope.enabled=false")
                .run(context -> assertTrue(
                        context.getBeanNamesForType(DataScopeInnerInterceptor.class).length == 0,
                        "显式关闭后不应再装配"));
    }

    @Test
    @DisplayName("显式 true 照常装配")
    void explicitTrueWires() {
        runner.withPropertyValues("jffast.mybatis.data-scope.enabled=true")
                .run(context -> assertNotNull(context.getBean(DataScopeInnerInterceptor.class)));
    }
}
