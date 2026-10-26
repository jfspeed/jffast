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
 * 定时任务新增/修改请求体。
 * 与实体分离：不接收审计字段，避免客户端伪造写入造成越权。
 *
 * @author jffast
 */
@Data
@Schema(description = "定时任务保存请求")
public class SysJobRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "任务名称不能为空")
    @Schema(description = "任务名称")
    private String jobName;

    @Schema(description = "任务分组")
    private String jobGroup;

    @NotBlank(message = "调用目标不能为空")
    @Schema(description = "调用目标（Bean名.方法名）")
    private String invokeTarget;

    @NotBlank(message = "cron 表达式不能为空")
    @Schema(description = "cron 表达式")
    private String cronExpression;

    @Schema(description = "执行策略")
    private String misfirePolicy;

    @Schema(description = "是否并发：0-允许，1-禁止")
    private Integer concurrent;

    @Schema(description = "状态：0-暂停，1-正常")
    private Integer status;

    @Schema(description = "执行服务名（多服务部署时标明归属，空=本服务）")
    private String serviceName;

    @Schema(description = "备注")
    private String remark;
}
