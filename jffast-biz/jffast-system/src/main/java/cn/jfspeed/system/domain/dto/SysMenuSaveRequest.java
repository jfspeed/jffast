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
 * 菜单新增/修改请求体。
 * <p>
 * 与实体分离：不接收 createBy / createTime / updateBy / updateTime / isDeleted，
 * 这些字段由服务端自动填充决定，避免客户端伪造写入。
 * <p>
 * 命名上刻意不与 {@link SysMenuRequest}（菜单对，用于交换排序）混淆。
 *
 * @author jffast
 */
@Data
@Schema(description = "菜单保存请求")
public class SysMenuSaveRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "菜单名称不能为空")
    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "父菜单ID")
    private Long parentId;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "路由地址")
    private String url;

    @Schema(description = "组件路径")
    private String path;

    @Schema(description = "类型：M-目录，C-菜单，F-按钮")
    private String type;

    @Schema(description = "状态：1-正常，0-停用")
    private Integer state;

    @Schema(description = "权限标识")
    private String perms;

    @Schema(description = "是否可见")
    private Boolean visible;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "高亮菜单（详情页保持父菜单高亮）")
    private String activeMenu;
}
