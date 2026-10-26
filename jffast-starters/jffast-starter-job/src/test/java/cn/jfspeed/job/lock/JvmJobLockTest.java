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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JVM 内任务互斥锁单元测试。
 * <p>
 * 重点关注 token 唯一性：同一毫秒内的两次抢锁必须拿到不同 token，
 * 否则第二次触发会误判「抢锁成功」（2026-09-24 评审缺陷的回归防线）。
 *
 * @author jffast
 */
class JvmJobLockTest {

    private final JvmJobLock lock = new JvmJobLock();

    @Test
    @DisplayName("首次抢锁成功返回 token")
    void firstTryLockSucceeds() {
        String token = lock.tryLock("job:once", 60_000L);
        assertNotNull(token);
    }

    @Test
    @DisplayName("锁未过期时第二次抢锁返回 null")
    void secondTryLockFails() {
        String first = lock.tryLock("job:repeat", 60_000L);
        assertNotNull(first);

        assertNull(lock.tryLock("job:repeat", 60_000L));
    }

    @Test
    @DisplayName("同一毫秒内两次抢锁不同 key 拿到不同 token")
    void tokensAreUnique() {
        String a = lock.tryLock("job:a", 60_000L);
        String b = lock.tryLock("job:b", 60_000L);

        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("过期后同 key 可重新抢到")
    void reLockAfterExpiry() throws Exception {
        String first = lock.tryLock("job:expire", 50L);
        assertNotNull(first);

        Thread.sleep(120L);
        String second = lock.tryLock("job:expire", 60_000L);
        assertNotNull(second);
        assertNotEquals(first, second);
    }
}
