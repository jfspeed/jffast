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
package cn.jfspeed.job.lock;

import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的执行锁（`SET NX PX` + Lua 释放，防误删别人持有的锁）。
 *
 * <p>为什么不直接用 {@code cn.jfspeed.redis.util.RedisUtil#tryLock}：
 * 它在抢锁失败时打 **ERROR** 日志。而"多个副本同时触发同一个 cron 任务、只有一个抢到锁"
 * 是**每次触发都会发生的正常现象**，用 ERROR 记录会把日志刷爆、还会掩盖真正的错误。
 * 这里抢不到锁只记 debug。
 *
 * @author jffast
 */
@Slf4j
public class RedisJobLock implements JobLock {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisJobLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String tryLock(String key, long ttlMillis) {
        String token = IdUtil.fastSimpleUUID();
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, token, ttlMillis, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(ok)) {
                log.debug("【定时任务】获取执行锁成功: {}", key);
                return token;
            }
            log.debug("【定时任务】执行锁已被占用，跳过本次执行: {}", key);
            return null;
        } catch (Exception e) {
            // Redis 抖动时**不能**默认放行（那会退化成多副本重复执行），宁可跳过本次
            log.error("【定时任务】获取执行锁异常，跳过本次执行: {}", key, e);
            return null;
        }
    }
}
