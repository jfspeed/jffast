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
import org.flowable.task.api.history.HistoricTaskInstance;

import java.io.Serializable;
import java.util.Date;

/**
 * 历史任务（已办 / 审批历史抽屉）对外视图对象。
 *
 * <p>与 {@link WfProcessDefinitionVO} 同理：**不要把 Flowable 实体直接返回给前端**。
 * 历史任务的实现类同样带引擎内部结构（{@code revision}、{@code originalPersistentState} 等），
 * 直接序列化会把内部状态整个吐出去（单条就能到几 KB），而前端要的扁平字段反而取不到。
 * 端到端实测曾在 {@code /flow/history/instance/page} 上发现这种"能返回但不合用"的响应。
 *
 * <p>字段与前端列一一对应（{@code pages/flow/task/done.vue} 与审批历史抽屉的 columns）：
 * name / assignee / startTime / endTime / durationInMillis / deleteReason / processInstanceId / processDefinitionName。
 *
 * @param id                    任务 ID
 * @param name                  任务（节点）名称
 * @param assignee              办理人
 * @param startTime             开始时间
 * @param endTime               结束时间
 * @param durationInMillis      耗时（毫秒）
 * @param deleteReason          处理结果（完成/取消原因）
 * @param processInstanceId     所属流程实例 ID
 * @param processDefinitionId   所属流程定义 ID
 * @param processDefinitionName 所属流程名称（引擎历史任务实体不含，由服务层补查）
 * @param taskDefinitionKey     节点定义 key
 * @author jffast
 */
@Schema(description = "历史任务")
public record WfHistoricTaskVO(
        @Schema(description = "任务ID") String id,
        @Schema(description = "任务名称") String name,
        @Schema(description = "办理人") String assignee,
        @Schema(description = "开始时间") Date startTime,
        @Schema(description = "结束时间") Date endTime,
        @Schema(description = "耗时(毫秒)") Long durationInMillis,
        @Schema(description = "处理结果") String deleteReason,
        @Schema(description = "流程实例ID") String processInstanceId,
        @Schema(description = "流程定义ID") String processDefinitionId,
        @Schema(description = "流程名称") String processDefinitionName,
        @Schema(description = "节点定义key") String taskDefinitionKey
) implements Serializable {

    /** 由 Flowable 实体映射为对外 VO（不含流程名称）。 */
    public static WfHistoricTaskVO from(HistoricTaskInstance task) {
        return from(task, null);
    }

    /** 由 Flowable 实体映射为对外 VO，并补充流程名称。 */
    public static WfHistoricTaskVO from(HistoricTaskInstance task, String processDefinitionName) {
        if (task == null) {
            return null;
        }
        return new WfHistoricTaskVO(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                task.getStartTime(),
                task.getEndTime(),
                task.getDurationInMillis(),
                task.getDeleteReason(),
                task.getProcessInstanceId(),
                task.getProcessDefinitionId(),
                processDefinitionName,
                task.getTaskDefinitionKey()
        );
    }
}
