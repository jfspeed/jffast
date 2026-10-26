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
import org.flowable.engine.runtime.ProcessInstance;

import java.io.Serializable;
import java.util.Date;

/**
 * 运行中流程实例（对外视图对象）。
 *
 * <p>与 {@link WfProcessDefinitionVO} 同样的理由：**不要把 Flowable 实体直接返回给前端**。
 * {@code ProcessInstanceEntityImpl} 同样带惰性属性，序列化时会抛
 * {@code Cannot invoke ... because "processEngineConfiguration" is null} →
 * 接口 {@code code=500}、页面空白。
 *
 * <p>特别提示这个坑很隐蔽：`GET /flow/process/instance/page` 在**没有运行中实例时是好的**
 * （空列表不触发序列化），只有真的有实例在跑才炸 —— 2026-09-22 端到端跑通审批流之后才暴露。
 *
 * @param id                        实例 ID
 * @param processDefinitionId       流程定义 ID
 * @param processDefinitionKey      流程标识
 * @param processDefinitionName     流程名称
 * @param processDefinitionVersion  流程版本
 * @param businessKey               业务标识
 * @param startTime                 开始时间
 * @param startUserId               发起人 ID
 * @param suspended                 是否挂起
 * @param activityId                当前节点 ID
 * @author jffast
 */
@Schema(description = "运行中流程实例")
public record WfProcessInstanceVO(
        @Schema(description = "实例ID") String id,
        @Schema(description = "流程定义ID") String processDefinitionId,
        @Schema(description = "流程标识") String processDefinitionKey,
        @Schema(description = "流程名称") String processDefinitionName,
        @Schema(description = "流程版本") Integer processDefinitionVersion,
        @Schema(description = "业务标识") String businessKey,
        @Schema(description = "开始时间") Date startTime,
        @Schema(description = "发起人") String startUserId,
        @Schema(description = "是否挂起") boolean suspended,
        @Schema(description = "当前节点") String activityId
) implements Serializable {

    /** 由 Flowable 实体映射为对外 VO（只读安全字段）。 */
    public static WfProcessInstanceVO from(ProcessInstance instance) {
        if (instance == null) {
            return null;
        }
        return new WfProcessInstanceVO(
                instance.getId(),
                instance.getProcessDefinitionId(),
                instance.getProcessDefinitionKey(),
                instance.getProcessDefinitionName(),
                instance.getProcessDefinitionVersion(),
                instance.getBusinessKey(),
                instance.getStartTime(),
                instance.getStartUserId(),
                instance.isSuspended(),
                instance.getActivityId()
        );
    }
}
