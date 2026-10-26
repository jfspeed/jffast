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

/**
 * 流程部署请求。
 *
 * @author jffast
 */
@Data
@Schema(description = "流程部署请求")
public class WfDeployRequest {

    @Schema(description = "部署名称")
    private String name;

    @Schema(description = "分类编码（对应 wf_category.category_code）")
    private String category;

    @Schema(description = "BPMN XML 内容（设计器保存或上传文件内容）")
    private String bpmnXml;

    @Schema(description = "资源文件名（如 leave.bpmn20.xml）")
    private String resourceName;
}
