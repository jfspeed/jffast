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
package cn.jfspeed.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 参数配置新增/修改请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入造成越权。
 *
 * @author jffast
 */
@Data
@Schema(description = "参数配置保存请求")
public class SysConfigRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "参数名称不能为空")
    @Schema(description = "参数名称")
    private String configName;

    @NotBlank(message = "参数键名不能为空")
    @Schema(description = "参数键名")
    private String configKey;

    @NotBlank(message = "参数键值不能为空")
    @Schema(description = "参数键值")
    private String configValue;

    @Schema(description = "备注")
    private String remark;
}
