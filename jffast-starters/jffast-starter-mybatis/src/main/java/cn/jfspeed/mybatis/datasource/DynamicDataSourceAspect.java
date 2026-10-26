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

import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * @DS 切面：方法级优先于类级，进入前压栈、退出后弹栈还原（支持嵌套）
 * <p>
 * 顺序高于事务切面，保证取连接前数据源已切换
 *
 * @author jffast
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class DynamicDataSourceAspect {

    @Around("@annotation(cn.jfspeed.mybatis.datasource.DS) || @within(cn.jfspeed.mybatis.datasource.DS)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DS ds = resolveDs(joinPoint);
        DynamicDataSourceContextHolder.push(ds == null ? "" : ds.value());
        try {
            return joinPoint.proceed();
        } finally {
            DynamicDataSourceContextHolder.pop();
        }
    }

    private DS resolveDs(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 方法级优先
        DS methodDs = method.getAnnotation(DS.class);
        if (methodDs != null) {
            return methodDs;
        }
        // 其次实现类/声明类上的类级
        Class<?> targetClass = joinPoint.getTarget() == null ? method.getDeclaringClass() : joinPoint.getTarget().getClass();
        DS classDs = targetClass.getAnnotation(DS.class);
        if (classDs != null) {
            return classDs;
        }
        return method.getDeclaringClass().getAnnotation(DS.class);
    }
}
