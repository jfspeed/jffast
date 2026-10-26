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
import lombok.Data;

/**
 * 菜单对（用于交换两条菜单的排序值，见 {@code SysMenuService#changeMenuSort}）。
 *
 * @author jffast
 */
@Data
@Schema(description = "菜单对")
public class SysMenuRequest {
    @Schema(description = "菜单idA")
    private Long menuIdA;

    @Schema(description = "菜单idB")
    private Long menuIdB;
}
