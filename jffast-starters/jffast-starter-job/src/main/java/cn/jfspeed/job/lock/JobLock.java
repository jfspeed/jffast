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

/**
 * 任务执行的分布式锁 —— 保证**同一个任务在任意时刻只有一个实例在执行**。
 *
 * <p>为什么需要它：Quartz 默认的**内存** RAMJobStore（非集群）下，同一服务的多个副本
 * 都会各自调度同一个任务；JDBC jobstore 在 `isClustered=false` 时同理
 * ⇒ 没有锁就会"每个副本跑一次"。
 *
 * @author jffast
 */
public interface JobLock {

    /**
     * 尝试获取锁（不等待）。
     *
     * @param key       锁 key
     * @param ttlMillis 锁的存活时间（毫秒），只用于兜底"实例崩溃/断电后锁没释放"
     * @return 获取成功返回 **持有凭证**（释放时必须带上，防误删别人的锁）；失败返回 {@code null}
     */
    String tryLock(String key, long ttlMillis);

    /*
     * ⚠️ 本接口**刻意不提供 unlock/release**（2026-09-24 移除，此前是死代码）：
     * 锁只靠 TTL 自然过期回收。原因是"执行耗时长于下次触发间隔"时，提前释放会让**慢半拍的副本**
     * 重复执行同一个 tick（锁 key 里带 scheduledFireTime，正是为了让不同 tick 互不干扰）。
     * 若将来有人"补上"释放调用，会重新引入该重复执行问题。
     */
}
