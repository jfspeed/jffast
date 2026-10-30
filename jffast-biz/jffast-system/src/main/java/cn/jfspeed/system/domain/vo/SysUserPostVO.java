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

import cn.jfspeed.system.entity.SysPost;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户岗位绑定（{@code SysUserVO.postList} 的元素）。
 *
 * <p>只保留前端消费的字段：{@code id}（岗位绑定回填与提交）、{@code postName}（展示）。
 *
 * @author jffast
 */
@Data
@Schema(description = "用户岗位")
public class SysUserPostVO {

    @Schema(description = "岗位ID")
    private Long id;

    @Schema(description = "岗位名称")
    private String postName;

    /**
     * 实体 → VO：只映射白名单字段。
     */
    public static SysUserPostVO from(SysPost post) {
        if (post == null) {
            return null;
        }
        SysUserPostVO vo = new SysUserPostVO();
        vo.setId(post.getId());
        vo.setPostName(post.getPostName());
        return vo;
    }
}
