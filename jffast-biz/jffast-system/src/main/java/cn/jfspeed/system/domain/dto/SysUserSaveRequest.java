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
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/修改请求体。
 * 与实体分离：不暴露 salt / createBy / dataScope 等敏感或派生字段。
 *
 * @author jffast
 */
@Data
@Schema(description = "用户保存请求")
public class SysUserSaveRequest {

    @Schema(description = "用户 ID（修改时必填）")
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 20, message = "用户名长度不能超过 20 个字符")
    @Schema(description = "用户名")
    private String userName;

    @Schema(description = "密码（RSA 加密，仅新增时传入）")
    private String password;

    // sys_user.real_name 是 NOT NULL，缺校验会在落库时抛 NOT NULL 违例，
    // 而该违例最终只能报成「系统繁忙」，与真实原因无关。
    @NotBlank(message = "姓名不能为空")
    @Size(max = 20, message = "姓名长度不能超过 20 个字符")
    @Schema(description = "姓名")
    private String realName;

    @Schema(description = "昵称")
    private String nickName;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "头像 URL")
    private String avatar;

    @Schema(description = "状态（1 正常 2 停用）")
    private Integer state;

    @Schema(description = "部门 ID")
    private Long deptId;

    @Schema(description = "角色列表（仅取 id）")
    private List<IdRef> roleList;

    @Schema(description = "部门列表（仅取 id）")
    private List<IdRef> deptList;

    @Data
    @Schema(description = "仅含 id 的引用")
    public static class IdRef {
        @Schema(description = "ID")
        private Long id;
    }
}
