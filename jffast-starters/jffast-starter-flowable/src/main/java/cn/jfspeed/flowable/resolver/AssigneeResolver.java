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
package cn.jfspeed.flowable.resolver;

import java.util.List;

/**
 * 审批人解析器 SPI。
 * <p>
 * Starter 不依赖 jffast-system（避免循环依赖），由业务模块（jffast-flow）提供实现，
 * 将 user/role/dept 类型的审批人配置解析为实际用户 ID 列表。
 * <p>
 * 实现类需注册为 Spring Bean，{@code AssigneeTaskListener} 会自动注入。
 * 若容器中无此 Bean，则只支持 {@code initiator} 类型审批人。
 *
 * @author jffast
 */
public interface AssigneeResolver {

    /**
     * 根据类型和配置值解析出实际审批人用户 ID 列表。
     *
     * @param type  审批人类型：user/role/dept（initiator 由 Listener 直接处理，不走此接口）
     * @param value 审批人值（用户ID/角色key/部门ID，多个用逗号分隔）
     * @return 用户 ID 列表（去重），无匹配返回空列表
     */
    List<String> resolveUserIds(String type, String value);
}
