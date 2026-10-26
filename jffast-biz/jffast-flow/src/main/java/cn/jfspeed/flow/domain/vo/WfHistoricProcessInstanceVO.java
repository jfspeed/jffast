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
import org.flowable.engine.history.HistoricProcessInstance;

import java.io.Serializable;
import java.util.Date;

/**
 * 历史流程实例对外视图对象。
 *
 * <p>与 {@link WfProcessDefinitionVO} 同理，不要直接返回 Flowable 实体。
 * 2026-09-22 实测 {@code /flow/history/instance/page} 原先返回
 * {@code {"id":...,"revision":1,"originalPersistentState":{...}}} —— 能把接口调通，
 * 但前端要的 {@code processDefinitionName}/{@code businessKey} 等字段都在内层，页面取不到值，
 * 且单条响应 6.8KB。
 *
 * @param id                        实例 ID
 * @param processDefinitionId       流程定义 ID
 * @param processDefinitionKey      流程标识
 * @param processDefinitionName     流程名称
 * @param processDefinitionVersion  流程版本
 * @param businessKey               业务标识
 * @param startTime                 开始时间
 * @param endTime                   结束时间
 * @param durationInMillis          耗时（毫秒）
 * @param startUserId               发起人
 * @param deleteReason              结束原因（正常结束为 null）
 * @param businessStatus            业务状态
 * @author jffast
 */
@Schema(description = "历史流程实例")
public record WfHistoricProcessInstanceVO(
        @Schema(description = "实例ID") String id,
        @Schema(description = "流程定义ID") String processDefinitionId,
        @Schema(description = "流程标识") String processDefinitionKey,
        @Schema(description = "流程名称") String processDefinitionName,
        @Schema(description = "流程版本") Integer processDefinitionVersion,
        @Schema(description = "业务标识") String businessKey,
        @Schema(description = "开始时间") Date startTime,
        @Schema(description = "结束时间") Date endTime,
        @Schema(description = "耗时(毫秒)") Long durationInMillis,
        @Schema(description = "发起人") String startUserId,
        @Schema(description = "结束原因") String deleteReason,
        @Schema(description = "业务状态") String businessStatus
) implements Serializable {

    /** 由 Flowable 实体映射为对外 VO。 */
    public static WfHistoricProcessInstanceVO from(HistoricProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return new WfHistoricProcessInstanceVO(
                instance.getId(),
                instance.getProcessDefinitionId(),
                instance.getProcessDefinitionKey(),
                instance.getProcessDefinitionName(),
                instance.getProcessDefinitionVersion(),
                instance.getBusinessKey(),
                instance.getStartTime(),
                instance.getEndTime(),
                instance.getDurationInMillis(),
                instance.getStartUserId(),
                instance.getDeleteReason(),
                instance.getBusinessStatus()
        );
    }
}
