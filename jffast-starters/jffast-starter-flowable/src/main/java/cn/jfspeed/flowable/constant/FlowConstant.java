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
package cn.jfspeed.flowable.constant;

/**
 * 工作流常量。
 * <p>
 * 审批人类型（assigneeType）：流程设计器以任务级 BPMN 扩展属性 {@code flowable:assigneeType}
 * 配置在 UserTask 上，业务层也可以同名流程变量传入；
 * {@code AssigneeTaskListener} 在任务创建时优先读任务级属性，其次读流程变量。
 *
 * @author jffast
 */
public final class FlowConstant {

    private FlowConstant() {
    }

    /**
     * 审批人类型键（任务级 BPMN 扩展属性 {@code flowable:assigneeType} / 同名流程变量）
     */
    public static final String EXT_ATTR_ASSIGNEE_TYPE = "assigneeType";

    /**
     * 审批人取值键（任务级 BPMN 扩展属性 / 同名流程变量；用户ID/角色key/部门ID，多个用逗号分隔）
     */
    public static final String EXT_ATTR_ASSIGNEE_VALUE = "assigneeValue";

    /**
     * 审批人类型：指定用户（value 为用户 ID 列表）
     */
    public static final String ASSIGNEE_TYPE_USER = "user";

    /**
     * 审批人类型：指定角色（value 为角色 key 列表，解析为该角色下所有用户）
     */
    public static final String ASSIGNEE_TYPE_ROLE = "role";

    /**
     * 审批人类型：指定部门（value 为部门 ID 列表，解析为部门下所有用户）
     */
    public static final String ASSIGNEE_TYPE_DEPT = "dept";

    /**
     * 审批人类型：发起人自己（value 忽略）
     */
    public static final String ASSIGNEE_TYPE_INITIATOR = "initiator";

    /**
     * 流程变量：发起人用户 ID（流程启动时自动写入，供审批人解析使用）
     */
    public static final String VAR_INITIATOR_USER_ID = "initiatorUserId";

    /**
     * 流程变量：发起人部门 ID
     */
    public static final String VAR_INITIATOR_DEPT_ID = "initiatorDeptId";

    /**
     * 业务表前缀
     */
    public static final String TABLE_PREFIX = "wf_";

    /**
     * Flowable 引擎表前缀
     */
    public static final String ENGINE_TABLE_PREFIX = "ACT_";
}
