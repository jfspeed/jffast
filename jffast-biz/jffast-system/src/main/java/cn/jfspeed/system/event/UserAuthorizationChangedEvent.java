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
 * 用户授权信息（角色/部门/deptId）变更事件：必须在事务内发布，
 * 监听器在提交后撤销目标用户会话；事务回滚则不撤销。
 *
 * @param userId 发生变更的用户 ID
 * @author jffast
 */
public record UserAuthorizationChangedEvent(Long userId) {
}
