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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.demomode.config.DemoModeProperties;

/**
 * 写短路拦截器单测（纯 Mockito，无 Spring 上下文）。
 *
 * @author jffast
 */
class DemoModeWriteBlockInterceptorTest {

    private static MappedStatement statement(String id) {
        MappedStatement ms = mock(MappedStatement.class);
        when(ms.getId()).thenReturn(id);
        return ms;
    }

    @Test
    @DisplayName("关闭时直接放行")
    void disabledProceeds() throws Throwable {
        DemoModeProperties properties = new DemoModeProperties();
        properties.setEnabled(false);
        DemoModeWriteBlockInterceptor interceptor = new DemoModeWriteBlockInterceptor(properties);

        MappedStatement ms = statement("cn.jfspeed.demo.mapper.DemoExampleMapper.insert");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{ms, new Object()});
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertEquals(1, result);
        verify(invocation).proceed();
    }

    @Test
    @DisplayName("开启后非豁免写操作短路返回 0 且不执行 SQL")
    void blocksWrites() throws Throwable {
        DemoModeProperties properties = new DemoModeProperties();
        properties.setEnabled(true);
        DemoModeWriteBlockInterceptor interceptor = new DemoModeWriteBlockInterceptor(properties);

        MappedStatement ms = statement("cn.jfspeed.demo.mapper.DemoExampleMapper.insert");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{ms, new Object()});

        Object result = interceptor.intercept(invocation);

        assertEquals(0, result);
        verify(invocation, never()).proceed();
    }

    @Test
    @DisplayName("豁免 Mapper 正常放行")
    void exemptProceeds() throws Throwable {
        DemoModeProperties properties = new DemoModeProperties();
        properties.setEnabled(true);
        properties.setExemptStatementPrefixes(List.of("SysLogMapper"));
        DemoModeWriteBlockInterceptor interceptor = new DemoModeWriteBlockInterceptor(properties);

        MappedStatement ms = statement("cn.jfspeed.system.mapper.SysLogMapper.insert");
        Invocation invocation = mock(Invocation.class);
        when(invocation.getArgs()).thenReturn(new Object[]{ms, new Object()});
        when(invocation.proceed()).thenReturn(1);

        Object result = interceptor.intercept(invocation);

        assertEquals(1, result);
        verify(invocation).proceed();
    }
}
