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
package cn.jfspeed.system.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cn.jfspeed.system.service.SysUserService;

/**
 * 会话失效唯一路径：事务提交后同步执行、回滚不执行、无事务不兜底（N-10）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationEventTimingTest {

    @Mock
    private SysUserService sysUserService;

    @InjectMocks
    private AuthorizationChangedListener listener;

    private static TransactionalEventListener annotation(String method, Class<?>... types) throws Exception {
        Method resolved = AuthorizationChangedListener.class.getDeclaredMethod(method, types);
        TransactionalEventListener marked = resolved.getAnnotation(TransactionalEventListener.class);
        assertNotNull(marked, "缺少 @TransactionalEventListener");
        return marked;
    }

    @Test
    @DisplayName("两个监听器都必须是 AFTER_COMMIT 且禁止无事务兜底执行")
    void listenersMustRunAfterCommitOnly() throws Exception {
        for (TransactionalEventListener marked : new TransactionalEventListener[]{
                annotation("onRoleAuthorizationChanged", RoleAuthorizationChangedEvent.class),
                annotation("onUserAuthorizationChanged", UserAuthorizationChangedEvent.class)}) {
            assertEquals(TransactionPhase.AFTER_COMMIT, marked.phase());
            assertFalse(marked.fallbackExecution());
        }
    }

    @Test
    @DisplayName("角色事件：仅授权字段变化时双源撤销绑定用户会话")
    void roleEventKicksOut() {
        listener.onRoleAuthorizationChanged(new RoleAuthorizationChangedEvent(7L, true));
        verify(sysUserService).kickoutByRoleId(7L);
    }

    @Test
    @DisplayName("角色事件：非授权字段变化或空事件不踢人")
    void nonAuthorizationRoleEventIgnored() {
        listener.onRoleAuthorizationChanged(new RoleAuthorizationChangedEvent(7L, false));
        listener.onRoleAuthorizationChanged(new RoleAuthorizationChangedEvent(null, true));
        listener.onRoleAuthorizationChanged(null);
        verify(sysUserService, never()).kickoutByRoleId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("用户事件：撤销目标会话；空事件忽略")
    void userEventKicksOut() {
        listener.onUserAuthorizationChanged(new UserAuthorizationChangedEvent(9L));
        listener.onUserAuthorizationChanged(new UserAuthorizationChangedEvent(null));
        listener.onUserAuthorizationChanged(null);
        verify(sysUserService).kickoutUser(9L);
        verify(sysUserService, never()).kickoutUser(null);
    }

    @Test
    @DisplayName("撤销失败只记录告警，不影响已提交的业务")
    void kickFailureIsSwallowed() {
        doThrow(new IllegalStateException("redis down")).when(sysUserService).kickoutByRoleId(7L);
        listener.onRoleAuthorizationChanged(new RoleAuthorizationChangedEvent(7L, true));
        verify(sysUserService).kickoutByRoleId(7L);
    }
}
