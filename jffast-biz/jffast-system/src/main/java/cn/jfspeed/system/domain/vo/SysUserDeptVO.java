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

import cn.jfspeed.system.entity.SysDept;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户部门绑定（{@code SysUserVO.deptList} 的元素）。
 *
 * <p>只保留前端消费的字段：{@code id}（部门绑定回填与提交）、{@code deptName}（展示）。
 *
 * @author jffast
 */
@Data
@Schema(description = "用户部门")
public class SysUserDeptVO {

    @Schema(description = "部门ID")
    private Long id;

    @Schema(description = "部门名称")
    private String deptName;

    /**
     * 实体 → VO：只映射白名单字段。
     */
    public static SysUserDeptVO from(SysDept dept) {
        if (dept == null) {
            return null;
        }
        SysUserDeptVO vo = new SysUserDeptVO();
        vo.setId(dept.getId());
        vo.setDeptName(dept.getDeptName());
        return vo;
    }
}
