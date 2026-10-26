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
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假申请新增/修改请求体。
 * 与实体分离：userId / userName / status / 审批字段由服务端维护，不暴露给客户端。
 *
 * @author jffast
 */
@Data
@Schema(description = "请假申请保存请求")
public class LeaveApplicationRequest {

    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "请选择请假类型")
    @Schema(description = "请假类型：annual-年假/sick-事假/personal-病假/maternity-产假/other-其他")
    private String leaveType;

    @NotNull(message = "请选择开始时间")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @NotNull(message = "请假时长不能为空")
    @Schema(description = "请假时长（天）")
    private BigDecimal duration;

    @Schema(description = "请假原因")
    private String reason;
}
