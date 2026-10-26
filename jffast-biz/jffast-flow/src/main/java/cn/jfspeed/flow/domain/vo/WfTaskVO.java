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
package cn.jfspeed.flow.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import org.flowable.task.api.Task;

import java.io.Serializable;
import java.util.Date;

/**
 * 运行中任务（待办 / 任务池 / 任务详情）对外视图对象。
 *
 * <p>与 {@link WfProcessDefinitionVO} 同理：**不要把 Flowable 实体直接返回给前端**。
 * {@code Task} 接口（{@code TaskInfo}）声明了 {@code getProcessVariables()}、
 * {@code getTaskLocalVariables()}、{@code getIdentityLinks()} 这些<b>会触发查询</b>的 getter，
 * 直接序列化会去引擎上下文外取数据（可能 NPE 或把流程变量整个吐出去）。
 *
 * <p>字段与前端列一一对应（{@code pages/flow/task/todo.vue} 的 columns）：
 * id / name / processInstanceId / processDefinitionName / assignee / createTime。
 *
 * @param id                    任务 ID
 * @param name                  任务（节点）名称
 * @param assignee              办理人（任务池模式下为空，前端据此显示"签收"）
 * @param processInstanceId     所属流程实例 ID
 * @param processDefinitionId   所属流程定义 ID
 * @param processDefinitionName 所属流程名称（引擎任务实体不含，由服务层补查）
 * @param taskDefinitionKey     节点定义 key
 * @param description           任务描述
 * @param category              任务分类
 * @param priority              优先级
 * @param createTime            创建时间
 * @param claimTime             签收时间
 * @param suspended             是否挂起
 * @author jffast
 */
@Schema(description = "运行中任务")
public record WfTaskVO(
        @Schema(description = "任务ID") String id,
        @Schema(description = "任务名称") String name,
        @Schema(description = "办理人") String assignee,
        @Schema(description = "流程实例ID") String processInstanceId,
        @Schema(description = "流程定义ID") String processDefinitionId,
        @Schema(description = "流程名称") String processDefinitionName,
        @Schema(description = "节点定义key") String taskDefinitionKey,
        @Schema(description = "任务描述") String description,
        @Schema(description = "任务分类") String category,
        @Schema(description = "优先级") int priority,
        @Schema(description = "创建时间") Date createTime,
        @Schema(description = "签收时间") Date claimTime,
        @Schema(description = "是否挂起") boolean suspended
) implements Serializable {

    /** 由 Flowable 实体映射为对外 VO（只读安全字段，不触碰惰性属性）。 */
    public static WfTaskVO from(Task task, String processDefinitionName) {
        if (task == null) {
            return null;
        }
        return new WfTaskVO(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                processDefinitionName,
                task.getTaskDefinitionKey(),
                task.getDescription(),
                task.getCategory(),
                task.getPriority(),
                task.getCreateTime(),
                task.getClaimTime(),
                task.isSuspended()
        );
    }
}
