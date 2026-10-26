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

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DynamicDataSourceAspect} 单元测试：@DS 解析优先级与入栈/弹栈还原。
 *
 * @author jffast
 */
class DynamicDataSourceAspectTest {

    private final DynamicDataSourceAspect aspect = new DynamicDataSourceAspect();

    @AfterEach
    void tearDown() {
        DynamicDataSourceContextHolder.clear();
    }

    @DS("classLevel")
    static class ClassLevelService {
        @DS("methodLevel")
        public String methodLevel() {
            return "m";
        }

        public String inheritsClassLevel() {
            return "c";
        }
    }

    static class PlainService {
        public String plain() {
            return "p";
        }
    }

    private ProceedingJoinPoint joinPoint(Object target, String methodName) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(target.getClass().getMethod(methodName));
        when(pjp.getTarget()).thenReturn(target);
        return pjp;
    }

    @Nested
    @DisplayName("@DS 解析优先级")
    class ResolveDs {

        @Test
        @DisplayName("方法级 @DS 优先于类级")
        void methodLevelWins() throws Throwable {
            ProceedingJoinPoint pjp = joinPoint(new ClassLevelService(), "methodLevel");
            AtomicReference<String> duringProceed = new AtomicReference<>();
            when(pjp.proceed()).thenAnswer(invocation -> {
                duringProceed.set(DynamicDataSourceContextHolder.peek());
                return "m";
            });

            assertEquals("m", aspect.around(pjp));
            assertEquals("methodLevel", duringProceed.get());
        }

        @Test
        @DisplayName("无方法级注解时取类级 @DS")
        void classLevelFallback() throws Throwable {
            ProceedingJoinPoint pjp = joinPoint(new ClassLevelService(), "inheritsClassLevel");
            AtomicReference<String> duringProceed = new AtomicReference<>();
            when(pjp.proceed()).thenAnswer(invocation -> {
                duringProceed.set(DynamicDataSourceContextHolder.peek());
                return "c";
            });

            assertEquals("c", aspect.around(pjp));
            assertEquals("classLevel", duringProceed.get());
        }

        @Test
        @DisplayName("无任何 @DS 时压入空串（路由按 primary）")
        void noAnnotationPushesEmpty() throws Throwable {
            ProceedingJoinPoint pjp = joinPoint(new PlainService(), "plain");
            AtomicReference<String> duringProceed = new AtomicReference<>();
            when(pjp.proceed()).thenAnswer(invocation -> {
                duringProceed.set(DynamicDataSourceContextHolder.peek());
                return "p";
            });

            assertEquals("p", aspect.around(pjp));
            assertEquals("", duringProceed.get());
        }
    }

    @Nested
    @DisplayName("栈还原语义")
    class StackRestore {

        @Test
        @DisplayName("外层嵌套恢复外层数据源")
        void nestedRestore() throws Throwable {
            ProceedingJoinPoint outer = joinPoint(new ClassLevelService(), "inheritsClassLevel");
            when(outer.proceed()).thenAnswer(invocation -> {
                ProceedingJoinPoint inner = joinPoint(new PlainService(), "plain");
                when(inner.proceed()).thenReturn("p");
                aspect.around(inner);
                assertEquals("classLevel", DynamicDataSourceContextHolder.peek());
                return "outer";
            });

            assertEquals("outer", aspect.around(outer));
            assertNull(DynamicDataSourceContextHolder.peek());
        }

        @Test
        @DisplayName("目标方法抛异常时依然弹栈")
        void popOnException() throws Throwable {
            ProceedingJoinPoint pjp = joinPoint(new PlainService(), "plain");
            when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

            assertThrows(IllegalStateException.class, () -> aspect.around(pjp));
            assertNull(DynamicDataSourceContextHolder.peek());
        }
    }
}
