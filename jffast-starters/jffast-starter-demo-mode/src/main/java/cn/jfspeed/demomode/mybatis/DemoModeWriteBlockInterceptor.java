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
package cn.jfspeed.demomode.mybatis;

import java.util.Properties;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.jfspeed.demomode.config.DemoModeProperties;

/**
 * 演示模式写短路拦截器：演示站所有 DB 写操作（insert/update/delete）
 * 不执行 SQL，直接返回 0 影响行，service 层照常包 {@code R.success}。
 * <p>
 * 查询不受影响；豁免表（默认操作/任务日志）正常入库。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
/**
 * @author jffast
 */
public class DemoModeWriteBlockInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(DemoModeWriteBlockInterceptor.class);

    private final DemoModeProperties properties;

    public DemoModeWriteBlockInterceptor(DemoModeProperties properties) {
        this.properties = properties;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (!properties.isEnabled()) {
            return invocation.proceed();
        }
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        if (isExempt(ms.getId())) {
            return invocation.proceed();
        }
        if (log.isDebugEnabled()) {
            log.debug("[DemoMode] 写操作已短路（未入库）: {}", ms.getId());
        }
        return 0;
    }

    boolean isExempt(String statementId) {
        if (statementId == null) {
            return false;
        }
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String namespace = statementId.substring(0, lastDot);
        String simpleName = namespace.substring(namespace.lastIndexOf('.') + 1);
        for (String prefix : properties.getExemptStatementPrefixes()) {
            if (simpleName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无额外属性
    }
}
