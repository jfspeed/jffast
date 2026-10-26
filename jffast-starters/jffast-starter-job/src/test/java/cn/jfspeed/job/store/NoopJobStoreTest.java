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
package cn.jfspeed.job.store;

import cn.jfspeed.job.domain.JobRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 无数据库时的空实现行为契约单元测试。
 *
 * @author jffast
 */
class NoopJobStoreTest {

    private final NoopJobStore store = new NoopJobStore();

    @Test
    @DisplayName("isAvailable 恒为 false（调度器据此安静跳过）")
    void notAvailable() {
        assertFalse(store.isAvailable());
    }

    @Test
    @DisplayName("查询类方法返回空结果而非抛异常")
    void queriesReturnEmpty() {
        assertTrue(store.listAll().isEmpty());
        assertNull(store.findByInvokeTarget("cn.jfspeed.DemoJob.execute"));
    }

    @Test
    @DisplayName("写入类方法为 no-op，可安全调用")
    void writesAreNoop() {
        store.insert(new JobRecord());
        store.insertLog(new cn.jfspeed.job.domain.JobLogRecord());
    }

    @Test
    @DisplayName("无存储可写 ⇒ 认领不成立（不得当成认领成功）")
    void claimNeverSucceeds() {
        assertFalse(store.claim(1L, "jffast-system"));
    }
}
