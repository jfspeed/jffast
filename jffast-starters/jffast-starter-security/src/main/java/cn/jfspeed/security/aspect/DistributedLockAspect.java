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

import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.hutool.extra.spring.SpringUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @author jffast
 */
@Slf4j
@Aspect
public class DistributedLockAspect {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 启动期反射自检缓存的 Method 句柄。
     * 通过 {@link #init()} 在应用启动时解析一次：若 RedisUtil 在类路径上但
     * tryLock/releaseLock 签名不匹配，则直接抛异常让应用启动失败（fail-fast），
     * 避免像此前那样“编译通过、运行到保存时才 NoSuchMethodException → 系统繁忙”。
     */
    private volatile Class<?> redisUtilClass;
    private volatile Method tryLockMethod;
    private volatile Method releaseLockMethod;

    @PostConstruct
    public void init() {
        try {
            Class<?> clazz = Class.forName("cn.jfspeed.redis.util.RedisUtil");
            this.redisUtilClass = clazz;
            this.tryLockMethod = clazz.getMethod("tryLock", String.class, String.class, long.class, int.class);
            this.releaseLockMethod = clazz.getMethod("releaseLock", String.class, String.class);
            log.info("DistributedLockAspect 反射自检通过：RedisUtil.tryLock(String,String,long,int) / releaseLock(String,String) 签名匹配");
        } catch (ClassNotFoundException e) {
            // RedisUtil 不在类路径（如某些不引入 redis 的场景）：保持运行时降级，不阻断启动
            log.warn("RedisUtil 不在类路径，分布式锁将在运行时降级为不生效");
            this.redisUtilClass = null;
            this.tryLockMethod = null;
            this.releaseLockMethod = null;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(
                    "分布式锁切面反射自检失败：RedisUtil 方法签名不匹配，请检查 cn.jfspeed.redis.util.RedisUtil。"
                            + "期望 tryLock(String,String,long,int) 与 releaseLock(String,String)。", e);
        }
    }

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint point, DistributedLock distributedLock) throws Throwable {
        String lockKey = buildLockKey(distributedLock.key(), point);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        long expireTime = distributedLock.expireTime();

        boolean lockSuccess = redisTryLock(lockKey, requestId, expireTime);
        if (!lockSuccess) {
            log.warn("获取分布式锁失败，lockKey：{}", lockKey);
            throw new BusinessException(distributedLock.message());
        }

        try {
            return point.proceed();
        } finally {
            boolean releaseSuccess = redisReleaseLock(lockKey, requestId);
            if (!releaseSuccess) {
                log.warn("释放分布式锁失败，lockKey：{}，requestId：{}", lockKey, requestId);
            }
        }
    }

    private boolean redisTryLock(String lockKey, String requestId, long expireTime) {
        if (redisUtilClass == null || tryLockMethod == null) {
            log.warn("RedisUtil 不可用，分布式锁降级为不生效");
            return true;
        }
        try {
            Object redisUtil = SpringUtil.getBean(redisUtilClass);
            Object result = tryLockMethod.invoke(redisUtil, lockKey, requestId, expireTime, 0);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("RedisUtil.tryLock failed", e);
            return false;
        }
    }

    private boolean redisReleaseLock(String lockKey, String requestId) {
        if (redisUtilClass == null || releaseLockMethod == null) {
            return true;
        }
        try {
            Object redisUtil = SpringUtil.getBean(redisUtilClass);
            Object result = releaseLockMethod.invoke(redisUtil, lockKey, requestId);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("RedisUtil.releaseLock failed", e);
            return false;
        }
    }

    private String buildLockKey(String key, ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getMethod().getName();
        String prefix = className + ":" + methodName + ":";
        return prefix + parseKey(key, point);
    }

    private String parseKey(String key, ProceedingJoinPoint point) {
        if (!key.contains("#")) {
            return key;
        }

        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] parameterNames = nameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = point.getArgs();

        EvaluationContext context = new StandardEvaluationContext();
        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        Expression expression = parser.parseExpression(key);
        Object value = expression.getValue(context);
        return value != null ? value.toString() : key;
    }
}
