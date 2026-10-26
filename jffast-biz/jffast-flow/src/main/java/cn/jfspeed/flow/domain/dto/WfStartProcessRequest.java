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
import lombok.Data;

import java.util.Map;

/**
 * 发起流程请求。
 *
 * @author jffast
 */
@Data
@Schema(description = "发起流程请求")
public class WfStartProcessRequest {

    @Schema(description = "流程定义 key（process key）")
    private String processKey;

    @Schema(description = "业务主键（如请假单 ID）")
    private String businessKey;

    @Schema(description = "流程名称（可选）")
    private String name;

    @Schema(description = "流程变量（含 assigneeType/assigneeValue 等审批人配置）")
    private Map<String, Object> variables;
}
