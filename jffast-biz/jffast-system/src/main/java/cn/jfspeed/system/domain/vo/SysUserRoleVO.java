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
package cn.jfspeed.system.domain.vo;

import cn.jfspeed.system.entity.SysRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户角色（{@code SysUserVO.roleList} 的元素）。
 *
 * <p>只保留前端消费的字段：{@code id}（提交角色绑定 / 派生超管位）、{@code roleKey}（超管位判定）、
 * {@code roleName}（展示）。角色实体上的 dataScope / menuIds / deptIds / remark 属内部授权结构，
 * 不出参。
 *
 * @author jffast
 */
@Data
@Schema(description = "用户角色")
public class SysUserRoleVO {

    @Schema(description = "角色ID")
    private Long id;

    @Schema(description = "角色标识")
    private String roleKey;

    @Schema(description = "角色名称")
    private String roleName;

    /**
     * 实体 → VO：只映射白名单字段。
     */
    public static SysUserRoleVO from(SysRole role) {
        if (role == null) {
            return null;
        }
        SysUserRoleVO vo = new SysUserRoleVO();
        vo.setId(role.getId());
        vo.setRoleKey(role.getRoleKey());
        vo.setRoleName(role.getRoleName());
        return vo;
    }
}
