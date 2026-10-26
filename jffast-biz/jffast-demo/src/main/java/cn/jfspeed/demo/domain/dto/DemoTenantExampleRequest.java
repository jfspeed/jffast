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
package cn.jfspeed.demo.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 租户隔离示例新增/修改请求体。
 * <p>
 * 与实体分离，且**刻意不含 tenantId**：租户归属一律由服务端按登录态写入，
 * 客户端伪造 tenantId 会直接绕过隔离，这正是示例模块要演示的反例。
 *
 * @author jffast
 */
@Data
@Schema(description = "租户隔离示例保存请求")
public class DemoTenantExampleRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "标题不能为空")
    @Schema(description = "标题")
    private String title;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "状态：0-停用，1-启用")
    private Integer status;
}
