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
package cn.jfspeed.security.aspect;

import cn.jfspeed.common.core.enums.FrameworkErrorCode;
import java.lang.reflect.Method;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.redis.util.RedisUtil;
import cn.jfspeed.security.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 幂等性切面
 * <p>
 * 基于 Redis SETNX 实现，key = idempotent:{userId}:{URI}:{paramsHash}
 *
 * @author jffast
 */
@Aspect
public class IdempotentAspect {

    private static final String CACHE_PREFIX = "idempotent:";

    @Around("@annotation(cn.jfspeed.security.annotation.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent idempotent = method.getAnnotation(Idempotent.class);

        String key = buildKey(joinPoint);
        if (StrUtil.isEmpty(key)) {
            // 无法生成 key（如未登录场景），直接放行
            return joinPoint.proceed();
        }

        String redisKey = CACHE_PREFIX + key;
        String requestId = "1";
        boolean acquired = RedisUtil.tryLock(redisKey, requestId, idempotent.expire() * 1000L, 0);
        if (!acquired) {
            // 裸 RuntimeException 会被兜底处理器映射成 500，重复提交应显式表达为 409
            throw new BusinessException(FrameworkErrorCode.CONFLICT.getCode(), idempotent.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 执行失败时删除 key，允许重试
            RedisUtil.del(redisKey);
            throw e;
        }
    }

    private String buildKey(ProceedingJoinPoint joinPoint) {
        StringBuilder sb = new StringBuilder();

        // 用户 ID
        try {
            Long userId = LoginUtil.getLoginUserId();
            sb.append(userId != null ? userId : "anonymous");
        } catch (Exception e) {
            sb.append("anonymous");
        }

        // URI
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            sb.append(":").append(request.getRequestURI());
        }

        // 参数 hash
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            StringBuilder params = new StringBuilder();
            for (Object arg : args) {
                if (arg != null) {
                    params.append(arg.toString());
                }
            }
            sb.append(":").append(SecureUtil.md5(params.toString()));
        }

        return sb.toString();
    }
}
