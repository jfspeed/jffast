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
import lombok.Data;

import java.math.BigDecimal;

/**
 * 示例明细行请求体：只收业务字段，exampleId / 排序由服务端补齐。
 *
 * @author jffast
 */
@Data
@Schema(description = "示例明细行")
public class DemoExampleItemRequest {

    @Schema(description = "明细ID（全量替换时忽略，由服务端重排）")
    private Long id;

    @Schema(description = "物料ID")
    private Long productId;

    @Schema(description = "物料名称")
    private String productName;

    @Schema(description = "数量")
    private Integer qty;

    @Schema(description = "单价")
    private BigDecimal price;

    @Schema(description = "排序；不传按数组下标")
    private Integer sort;
}
