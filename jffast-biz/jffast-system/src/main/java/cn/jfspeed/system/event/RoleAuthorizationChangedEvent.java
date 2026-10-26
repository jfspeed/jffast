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

/**
 * 角色授权字段（roleKey/dataScope/deptIds 集合）变更事件：必须在事务内发布，
 * 监听器在提交后统一撤销绑定用户会话；事务回滚则不撤销。只在确实发生变化时发布，
 * 因此 {@code authFieldsChanged} 恒为 true（保留字段以固定事件契约）。
 *
 * @param roleId           发生变更的角色 ID
 * @param authFieldsChanged 授权字段是否变化（当前发布路径恒为 true）
 * @author jffast
 */
public record RoleAuthorizationChangedEvent(Long roleId, boolean authFieldsChanged) {
}
