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

import cn.jfspeed.common.core.util.page.PageReq;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(title = "系统岗位查询请求")
public class SysPostQueryRequest extends PageReq {

    @Schema(title = "岗位编码")
    private String postCode;
    @Schema(title = "岗位名称")
    private String postName;
    @Schema(title = "状态（1正常 0停用）")
    private Integer state;
}
