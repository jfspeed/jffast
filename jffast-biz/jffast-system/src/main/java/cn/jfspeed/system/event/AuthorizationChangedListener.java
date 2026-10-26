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

import cn.jfspeed.system.service.SysUserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 授权变更的唯一会话失效路径：事务提交后同步处理，回滚不执行。
 * <p>不添加 {@code @Async}：提交后立即撤销，避免请求返回后旧会话仍可用；
 * {@code fallbackExecution=false} 保证无事务调用不会误当已提交处理。</p>
 *
 * @author jffast
 */
@Slf4j
@Component
public class AuthorizationChangedListener {

    @Resource
    private SysUserService sysUserService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onRoleAuthorizationChanged(RoleAuthorizationChangedEvent event) {
        if (event == null || event.roleId() == null || !event.authFieldsChanged()) {
            return;
        }
        try {
            sysUserService.kickoutByRoleId(event.roleId());
        } catch (Exception e) {
            log.warn("角色授权变更后撤销会话失败，roleId={}", event.roleId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onUserAuthorizationChanged(UserAuthorizationChangedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        try {
            sysUserService.kickoutUser(event.userId());
        } catch (Exception e) {
            log.warn("用户授权变更后撤销会话失败，userId={}", event.userId(), e);
        }
    }
}
