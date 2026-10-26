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

import cn.jfspeed.common.core.util.page.PageReq;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "请假申请分页查询请求")
public class LeaveApplicationQueryRequest extends PageReq {

    @Schema(description = "申请人姓名（模糊）")
    private String userName;

    @Schema(description = "请假类型")
    private String leaveType;

    @Schema(description = "状态：0-待审批/1-已通过/2-已拒绝/3-已撤回")
    private Integer status;
}
