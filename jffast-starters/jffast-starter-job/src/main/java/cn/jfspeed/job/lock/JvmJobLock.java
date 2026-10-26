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

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无 Redis 时的兜底实现：**只在单个 JVM 内**互斥。
 *
 * <p>它只能防止"同一进程里同一任务被并发触发"，**不能**阻止同一服务的多个副本各跑一次 ——
 * 所以本类构造时会打一条 WARN，提示"要真正防重复请提供 Redis"。
 *
 * @author jffast
 */
@Slf4j
public class JvmJobLock implements JobLock {

    private final Map<String, Entry> holds = new ConcurrentHashMap<>();

    public JvmJobLock() {
        log.warn("【定时任务】未检测到 Redis，任务执行锁退化为**单 JVM** 锁："
                + "同一服务的多个副本仍会各执行一次（多实例部署请为服务配置 Redis）");
    }

    @Override
    public String tryLock(String key, long ttlMillis) {
        long now = System.currentTimeMillis();
        // token 必须**每次抢锁都唯一**：旧写法 `毫秒 + key.hashCode()` 在同一毫秒内的两次触发会算出相同 token，
        // 第二个触发会误判"抢锁成功"（2026-09-24 评审发现）。这里改用 UUID。
        String token = cn.hutool.core.util.IdUtil.fastSimpleUUID();
        Entry entry = holds.compute(key, (k, old) -> {
            if (old == null || old.expireAt <= now) {
                return new Entry(token, now + ttlMillis);
            }
            return old;
        });
        return token.equals(entry.token) ? token : null;
    }



    private record Entry(String token, long expireAt) {
    }
}
