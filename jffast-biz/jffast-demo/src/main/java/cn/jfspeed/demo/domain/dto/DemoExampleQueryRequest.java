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
@Schema(description = "示例分页查询请求")
public class DemoExampleQueryRequest extends PageReq {

    @Schema(description = "标题（模糊）")
    private String title;

    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "是否推荐：0-否，1-是")
    private Integer isRecommend;
}
