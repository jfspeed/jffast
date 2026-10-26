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
package cn.jfspeed.flow.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 完成任务请求（审批）。
 *
 * @author jffast
 */
@Data
@Schema(description = "完成任务请求")
public class WfCompleteTaskRequest {

    @NotBlank(message = "任务 ID 不能为空")
    @Size(max = 64, message = "任务 ID 长度不能超过 64")
    @Schema(description = "任务 ID")
    private String taskId;

    @Size(max = 500, message = "审批意见长度不能超过 500")
    @Schema(description = "审批意见")
    private String comment;

    @Size(max = 100, message = "流程变量数量不能超过 100")
    @Schema(description = "流程变量（可含下一节点审批人配置 assigneeType/assigneeValue）")
    private Map<String, Object> variables;
}
