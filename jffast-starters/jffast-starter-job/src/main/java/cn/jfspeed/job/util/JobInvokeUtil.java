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
package cn.jfspeed.job.util;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.job.annotation.JfFastJob;
import cn.jfspeed.job.config.JobProperties;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author jffast
 */
public class JobInvokeUtil {

    /**
     * 调用目标里的 Bean 名（{@code beanName.methodName} / {@code beanName.methodName(params)} 的第一段）。
     *
     * @return Bean 名；调用目标为空或没有 {@code '.'} 时返回 null
     */
    public static String resolveBeanName(String invokeTarget) {
        if (StrUtil.isBlank(invokeTarget)) {
            return null;
        }
        int dotIndex = invokeTarget.indexOf('.');
        return dotIndex < 0 ? null : invokeTarget.substring(0, dotIndex);
    }

    /**
     * 本进程里有没有这个调用目标对应的 Bean —— 也就是**本服务能不能执行这条任务**。
     *
     * <p>用于任务归属认领：只认领自己跑得动的任务。
     * 认领一个跑不动的任务，会把别的服务（classpath 里有那个 Bean）挡在门外，
     * 结果就是任务永远没人执行。
     *
     * @return 没有 Spring 上下文、调用目标格式不对、或 Bean 不在本进程时为 false
     */
    public static boolean isBeanPresent(String invokeTarget) {
        String beanName = resolveBeanName(invokeTarget);
        if (beanName == null) {
            return false;
        }
        org.springframework.context.ApplicationContext ctx = SpringContextHolder.getApplicationContext();
        return ctx != null && ctx.containsBean(beanName);
    }

    public static void invokeMethod(String invokeTarget) throws Exception {
        if (StrUtil.isBlank(invokeTarget)) {
            throw new IllegalArgumentException("调用目标不能为空");
        }
        String beanName;
        String methodName;
        String paramsStr = null;

        int dotIndex = invokeTarget.indexOf('.');
        if (dotIndex < 0) {
            throw new IllegalArgumentException("调用目标格式错误，正确格式: beanName.methodName 或 beanName.methodName(params)");
        }
        beanName = invokeTarget.substring(0, dotIndex);
        String remaining = invokeTarget.substring(dotIndex + 1);

        int parenIndex = remaining.indexOf('(');
        if (parenIndex >= 0) {
            methodName = remaining.substring(0, parenIndex);
            int closeParen = remaining.indexOf(')', parenIndex);
            if (closeParen > parenIndex + 1) {
                paramsStr = remaining.substring(parenIndex + 1, closeParen);
            }
        } else {
            methodName = remaining;
        }

        Object bean = SpringContextHolder.getBean(beanName);
        Method method;
        Object[] args;

        if (StrUtil.isNotBlank(paramsStr)) {
            method = ReflectionUtils.findMethod(bean.getClass(), methodName, String.class);
            if (method == null) {
                throw new NoSuchMethodException(bean.getClass().getName() + "." + methodName + "(String)");
            }
            args = new Object[]{paramsStr};
        } else {
            method = ReflectionUtils.findMethod(bean.getClass(), methodName);
            if (method == null) {
                throw new NoSuchMethodException(bean.getClass().getName() + "." + methodName + "()");
            }
            args = new Object[0];
        }

        ReflectionUtils.makeAccessible(method);
        checkInvokeAllowed(bean, method, invokeTarget);
        method.invoke(bean, args);
    }

    /**
     * 调用目标白名单（收敛 RCE 面：拿到任务管理权限不能借此调任意 Bean 的任意方法）。
     * <p>只允许：① 标注 {@code @JfFastJob} 的任务方法（任意包）；
     * ② 声明在允许包前缀内的方法（默认无允许包，只认 {@code @JfFastJob}；
     * 确有非注解任务时用 {@code jffast.job.invoke.allowed-packages} 显式追加）。
     * Object / 框架类上声明的 {@code toString}、{@code close}、{@code destroy} 等一律拒绝。
     */
    static void checkInvokeAllowed(Object bean, Method method, String invokeTarget) {
        if (method.isAnnotationPresent(JfFastJob.class) || targetMethodHasJobAnnotation(bean, method)) {
            return;
        }
        String pkg = method.getDeclaringClass().getPackageName();
        for (String allowed : allowedPackages()) {
            if (pkg.equals(allowed) || pkg.startsWith(allowed + ".")) {
                return;
            }
        }
        throw new IllegalArgumentException("调用目标不在白名单内，禁止执行: " + invokeTarget
                + "（仅允许 @JfFastJob 声明的方法；确有非注解任务时通过 "
                + "jffast.job.invoke.allowed-packages 配置允许包）");
    }

    /** 被 CGLIB / JDK 代理时方法注解要到目标类上找（代理类覆盖方法不带注解） */
    private static boolean targetMethodHasJobAnnotation(Object bean, Method method) {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
        if (targetClass == null || targetClass == bean.getClass()) {
            return false;
        }
        Method targetMethod = ReflectionUtils.findMethod(targetClass, method.getName(), method.getParameterTypes());
        return targetMethod != null && targetMethod.isAnnotationPresent(JfFastJob.class);
    }

    private static List<String> allowedPackages() {
        JobProperties properties = SpringContextHolder.tryGetBean(JobProperties.class);
        List<String> packages = properties == null ? null : properties.getInvoke().getAllowedPackages();
        return packages == null ? List.of() : packages;
    }
}
