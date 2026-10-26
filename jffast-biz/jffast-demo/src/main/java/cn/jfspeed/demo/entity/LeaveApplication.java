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
package cn.jfspeed.demo.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 请假申请实体
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("demo_leave_application")
@Schema(description = "请假申请")
public class LeaveApplication extends BaseEntity {

    @TableField("user_id")
    @Schema(description = "申请人ID")
    private Long userId;

    @TableField("user_name")
    @Schema(description = "申请人姓名")
    private String userName;

    @TableField("dept_id")
    @Schema(description = "申请部门ID")
    private Long deptId;

    @TableField("leave_type")
    @Schema(description = "请假类型：annual-年假/sick-事假/personal-病假/maternity-产假/other-其他")
    private String leaveType;

    @TableField("start_time")
    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @TableField("end_time")
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @TableField("duration")
    @Schema(description = "请假时长（天）")
    private BigDecimal duration;

    @TableField("reason")
    @Schema(description = "请假原因")
    private String reason;

    @TableField("status")
    @Schema(description = "状态：0-待审批/1-已通过/2-已拒绝/3-已撤回")
    private Integer status;

    @TableField("approver_id")
    @Schema(description = "审批人ID")
    private Long approverId;

    @TableField("approver_name")
    @Schema(description = "审批人姓名")
    private String approverName;

    @TableField("approve_time")
    @Schema(description = "审批时间")
    private LocalDateTime approveTime;

    @TableField("approve_remark")
    @Schema(description = "审批备注")
    private String approveRemark;
}
