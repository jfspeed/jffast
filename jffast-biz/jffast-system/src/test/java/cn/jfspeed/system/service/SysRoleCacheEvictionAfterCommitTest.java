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
package cn.jfspeed.system.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.redis.util.RedisUtil;

/**
 * 角色权限缓存的失效时机：必须在事务提交之后删，不能在事务内删。
 * <p>
 * 事务内删键时，本方事务还没提交，旧绑定对其它读事务照样可见；紧跟其后的鉴权请求发现缓存空了，
 * 就把<b>旧</b>权限集重新写回并存活整个 TTL（3600 秒）。表现是"给角色收回了菜单，用户照旧点得动"，
 * 而且一小时后才自己好——排障时极易误判成前端缓存。
 * <p>
 * 每个用例都成对断言：提交前一次 Redis 写都没发生，提交后确实各删了一次。
 * 只断言"提交后删了"是假阳性——事务内删完再触发一次也照样过。
 *
 * @author jffast
 */
class SysRoleCacheEvictionAfterCommitTest {

    private static final Long ROLE_ID = 9L;
    private static final String PERMS_KEY = "jffast:role:perms:" + ROLE_ID;

    private final SysRoleMenuService service = new SysRoleMenuService();

    private MockedStatic<RedisUtil> redis;

    @BeforeEach
    void setUp() {
        redis = mockStatic(RedisUtil.class);
    }

    @AfterEach
    void tearDown() {
        redis.close();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务内：提交前不得删缓存，提交后才删")
    void evictionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.clearRoleCache(ROLE_ID);

            redis.verify(() -> RedisUtil.del(anyString()), never());
            redis.verify(() -> RedisUtil.hdel(anyString(), anyString()), never());

            fireAfterCommit();

            redis.verify(() -> RedisUtil.del(PERMS_KEY));
            redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ROLE_PERM_LIST, String.valueOf(ROLE_ID)));
            redis.verify(() -> RedisUtil.hdel(BaseConstant.CACHE_ALL_MENU_ID_LIST, String.valueOf(ROLE_ID)));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务外：立即失效，不能挂到永远不来的提交上")
    void evictionRunsAtOnceWithoutTransaction() {
        service.clearRoleCache(ROLE_ID);

        redis.verify(() -> RedisUtil.del(PERMS_KEY));
        redis.verify(() -> RedisUtil.hdel(eq(BaseConstant.CACHE_ALL_MENU_ID_LIST), eq(String.valueOf(ROLE_ID))));
    }

    @Test
    @DisplayName("全量失效同样等到提交后（菜单变更走这条）")
    void bulkEvictionAlsoWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.clearAllRoleCache();

            redis.verify(() -> RedisUtil.del(anyString()), never());
            redis.verify(() -> RedisUtil.allKey(anyString()), never());

            fireAfterCommit();

            redis.verify(() -> RedisUtil.allKey("jffast:role:perms:*"));
            redis.verify(() -> RedisUtil.del(BaseConstant.CACHE_ROLE_PERM_LIST));
            redis.verify(() -> RedisUtil.del(BaseConstant.CACHE_ALL_MENU_ID_LIST));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("角色 ID 为空：不注册同步器，免得挂一个空动作")
    void nullRoleIdRegistersNothing() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.clearRoleCache(null);

            List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
            assertTrue(registered.isEmpty(), "空角色 ID 不应注册事务同步器，实际注册了 " + registered.size() + " 个");
            redis.verify(() -> RedisUtil.del(anyString()), never());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /** 模拟容器提交：只回调 afterCommit，钩子顺序与 Spring 一致 */
    private void fireAfterCommit() {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }
}
