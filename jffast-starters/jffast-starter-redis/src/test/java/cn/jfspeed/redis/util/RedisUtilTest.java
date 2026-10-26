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
package cn.jfspeed.redis.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * RedisUtil 的委托与边界回归测试（连接由 mock 提供，不发真实命令）。
 *
 * <p>钉住的是**容易在改动中悄悄变质**的行为：
 * <ol>
 *   <li>过期时间口径：{@code expire/set} 传 0 或负数时**不能**写出一个「立即过期」的键，
 *       而是退化成永不过期——验证码、幂等锁都依赖它；</li>
 *   <li>读失败必须 fail-safe 返回 false，而不是把 Redis 故障升级成 500；</li>
 *   <li>删除的单键/多键两种调用形态（多键走集合重载，误用会静默不删）；</li>
 *   <li>释放锁走 Lua 原子比较删除，Java 侧不能再补一次 delete；</li>
 *   <li>自增/自减的负数入参是调用方错误，要显式抛出而不是写进 Redis。</li>
 * </ol>
 *
 * <p>JUnit 默认每个用例一个新实例，所以这里的 mock 天然按用例隔离，正例与反例的
 * {@code verify(..., never())} 必须写在**不同**用例里才有效。
 *
 * @author jffast
 */
class RedisUtilTest {

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> template = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void injectTemplate() {
        // RedisUtil 用静态字段持有模板（构造注入），new 一次即完成装配
        new RedisUtil(template);
        when(template.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("set 传正数过期时间时按秒写入 TTL")
    void setAppliesTtlWhenPositive() {
        assertTrue(RedisUtil.set("jffast:k", "v", 60));

        verify(valueOps).set("jffast:k", "v", 60L, TimeUnit.SECONDS);
        verify(valueOps, never()).set("jffast:k", "v");
    }

    @Test
    @DisplayName("set 传 0 或负数退化为永不过期，而不是写出立即过期的键")
    void setWithoutTtlWhenNonPositive() {
        assertTrue(RedisUtil.set("jffast:k", "v", 0));

        verify(valueOps).set("jffast:k", "v");
        verify(valueOps, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("get 原样取回；strLen 对缺失键返回 0")
    void getAndLength() {
        when(valueOps.get("jffast:str")).thenReturn("abc");
        assertEquals("abc", RedisUtil.get("jffast:str"));
        assertEquals(3L, RedisUtil.strLen("jffast:str"));

        when(valueOps.get("jffast:missing")).thenReturn(null);
        assertEquals(0L, RedisUtil.strLen("jffast:missing"));
    }

    @Test
    @DisplayName("expire 正数才下发 TTL")
    void expireAppliesPositiveTtl() {
        assertTrue(RedisUtil.expire("jffast:k", 30));

        verify(template).expire("jffast:k", 30L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("expire 传 0/负数直接返回 true 且完全不动键")
    void expireSkipsNonPositive() {
        assertTrue(RedisUtil.expire("jffast:k", 0));
        assertTrue(RedisUtil.expire("jffast:k", -1));

        verify(template, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("hasKey 在 Redis 故障时返回 false（fail-safe，不得把故障升级成 500）")
    void hasKeyFailsSafe() {
        when(template.hasKey("jffast:ok")).thenReturn(Boolean.TRUE);
        assertTrue(RedisUtil.hasKey("jffast:ok"));

        when(template.hasKey("jffast:down")).thenThrow(new IllegalStateException("redis down"));
        assertFalse(RedisUtil.hasKey("jffast:down"));
    }

    @Test
    @DisplayName("getExpire 透传剩余秒数")
    void getExpirePassesThrough() {
        when(template.getExpire("jffast:k", TimeUnit.SECONDS)).thenReturn(12L);
        assertEquals(12L, RedisUtil.getExpire("jffast:k"));
    }

    @Test
    @DisplayName("del 单键走 delete(K)，不能误用集合重载（会静默不删）")
    void deleteSingleKey() {
        RedisUtil.del("jffast:a");

        verify(template).delete("jffast:a");
        verify(template, never()).delete(any(java.util.Collection.class));
    }

    @Test
    @DisplayName("del 多键走 delete(Collection)，空参数不触碰模板")
    void deleteMultipleKeysAndNoKeys() {
        RedisUtil.del("jffast:b", "jffast:c");
        verify(template).delete(any(java.util.Collection.class));

        RedisUtil.del();
        verify(template, never()).delete("jffast:b");
    }

    @Test
    @DisplayName("incr/decr 正数走原子自增自减，负数是调用方错误必须显式抛出")
    void countersValidateDelta() {
        when(valueOps.increment("jffast:i", 1L)).thenReturn(7L);
        assertEquals(7L, RedisUtil.incr("jffast:i", 1L));

        when(valueOps.decrement("jffast:d", 2L)).thenReturn(5L);
        assertEquals(5L, RedisUtil.decr("jffast:d", 2L));

        assertThrows(RuntimeException.class, () -> RedisUtil.incr("jffast:i", -1L));
        assertThrows(RuntimeException.class, () -> RedisUtil.decr("jffast:d", -1L));
    }

    @Test
    @DisplayName("releaseLock 用 Lua 原子比较删除：脚本返回 1 才算成功，Java 侧不再补 delete")
    void releaseLockRunsLuaCompareAndDelete() {
        when(template.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList("jffast:lock")), eq("owner"))).thenReturn(1L);

        assertTrue(RedisUtil.releaseLock("jffast:lock", "owner"));
        verify(template, never()).delete(anyString());
    }

    @Test
    @DisplayName("releaseLock：token 不匹配时脚本返回 0，必须 false 且不删键")
    void releaseLockDeniedWhenTokenMismatch() {
        when(template.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(Collections.singletonList("jffast:lock2")), eq("mine"))).thenReturn(0L);

        assertFalse(RedisUtil.releaseLock("jffast:lock2", "mine"));
        verify(template, never()).delete(anyString());
    }

    @Test
    @DisplayName("releaseLock：脚本无返回值（连接异常）按失败处理，不抛给调用方")
    void releaseLockTreatsNullResultAsFailure() {
        assertFalse(RedisUtil.releaseLock("jffast:lock3", "x"));
        verify(template, never()).delete(anyString());
    }

    @Test
    @DisplayName("tryLock 默认不重试：第一次 setIfAbsent 失败就返回 false（只下发一次命令）")
    void tryLockDoesNotRetryByDefault() {
        when(valueOps.setIfAbsent("jffast:lock", "owner", 3000L, TimeUnit.MILLISECONDS))
                .thenReturn(Boolean.FALSE);

        assertFalse(RedisUtil.tryLock("jffast:lock", "owner"));
        verify(valueOps).setIfAbsent("jffast:lock", "owner", 3000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("limitCountAndCheck：首次累加设窗口 TTL，超过上限返回 false")
    void limitCountAndCheckUsesWindow() {
        when(valueOps.increment("jffast:limit", 1L)).thenReturn(1L);

        assertTrue(RedisUtil.limitCountAndCheck("jffast:limit", 300L, 5));
        verify(template).expire("jffast:limit", 300L, TimeUnit.SECONDS);

        when(valueOps.increment("jffast:limit6", 1L)).thenReturn(6L);
        assertFalse(RedisUtil.limitCountAndCheck("jffast:limit6", 300L, 5));
        verify(template, never()).expire(eq("jffast:limit6"), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("allKey 收拢游标结果并关闭游标（漏关会泄露 Redis 连接）")
    @SuppressWarnings("unchecked")
    void allKeyClosesCursor() throws Exception {
        org.springframework.data.redis.core.Cursor<String> cursor =
                mock(org.springframework.data.redis.core.Cursor.class);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("jffast:hit");
        when(template.scan(any(org.springframework.data.redis.core.ScanOptions.class)))
                .thenReturn(cursor);

        assertEquals(1, RedisUtil.allKey("jffast:*").length);
        verify(cursor).close();
    }
}
