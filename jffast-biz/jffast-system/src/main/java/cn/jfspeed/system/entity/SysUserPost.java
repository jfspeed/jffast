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
package cn.jfspeed.system.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户岗位关联
 *
 * <p>刻意不声明 {@code tenantId}：表上有该列，但字段一旦进实体，MyBatis-Plus 生成的 INSERT
 * 就会带上列名，租户拦截器不再注入取值，无登录上下文的路径（启动 Runner、定时任务）会写出
 * {@code tenant_id = NULL}。与 {@link SysUserRole} / {@link SysUserDept} 走同一条注入路径。
 *
 * @author jffast
 */
@Data
@TableName("sys_user_post")
public class SysUserPost {

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 岗位ID
     */
    @TableField(value = "post_id")
    private Long postId;

    public SysUserPost() {}

    public SysUserPost(Long userId, Long postId) {
        this.userId = userId;
        this.postId = postId;
    }
}
