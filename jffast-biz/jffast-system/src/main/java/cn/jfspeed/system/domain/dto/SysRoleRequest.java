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
 * 角色新增/修改请求体。
 * 与实体分离：不接收 createBy / createTime / updateBy / updateTime / isDeleted / tenantId，
 * 这些字段由服务端（自动填充 / 登录态）决定，避免客户端伪造写入造成越权。
 *
 * @author jffast
 */
@Data
@Schema(description = "角色保存请求")
public class SysRoleRequest {

    /** ID（新增时留空由服务端生成，修改时必填） */
    @Schema(description = "ID（修改时必填）")
    private Long id;

    @NotBlank(message = "角色标识不能为空")
    @Schema(description = "角色标识")
    private String roleKey;

    @NotBlank(message = "角色名称不能为空")
    @Schema(description = "角色名称")
    private String roleName;

    @Schema(description = "是否开启")
    private Boolean isOpen;

    @Schema(description = "数据权限范围")
    private Integer dataScope;

    @Schema(description = "备注")
    private String remark;

    /** 菜单ID集合（非表字段，仅用于绑定角色菜单） */
    @Schema(description = "菜单ID集合")
    private Long[] menuIds;

    /** 部门ID集合（非表字段，数据权限=自定义时有效） */
    @Schema(description = "部门ID集合")
    private Long[] deptIds;
}
