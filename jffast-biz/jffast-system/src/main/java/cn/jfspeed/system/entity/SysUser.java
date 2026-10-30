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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;

import cn.jfspeed.common.core.annotation.SensitiveMask;
import cn.jfspeed.common.data.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户信息
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("user_name")
    private String userName;

    /**
     * 用户名称
     */
    @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 1, suffixKeep = 1)
    @TableField("real_name")
    private String realName;

    /**
     * 角色id，多个id用逗号隔开
     * */
    @TableField("role_id")
    private String roleId;

    /**
     * 密码
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("password")
    private String password;

    /**
     * 盐*
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("salt")
    private String salt;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;
    /**
     * 昵称
     */
    @TableField("nick_name")
    private String nickName;

    /**
     * 手机号码
     */
    @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 3, suffixKeep = 4)
    @TableField("phone")
    private String phone;
    /**
     * email
     */
    @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 2, suffixKeep = 4)
    @TableField("email")
    private String email;

    /**
     * 最后登录时*
     */
    @TableField("login_date")
    private String loginDate;

    /**
     * 状态（1正常 2停用）
     */
    @TableField("state")
    private Integer state;

    /**
     * 密码更改时间
     */
    @TableField("update_pwd_time")
    private LocalDateTime updatePwdTime;

    @TableField("dept_id")
    private Long deptId;

    @TableField(exist = false)
    private String deptName;

    @TableField(exist = false)
    private String deptIds;

    @TableField(exist = false)
    private String roleIds;

    @TableField(exist = false)
    private String roleName;

    @TableField(exist = false)
    private Long parentId;

    /**
     * 查询的数据范围条*
     * */
    @TableField(exist = false)
    private Map<String,Object> dataScope;

    /**
     * 用户对应角色相关信息
     * */
    @TableField(exist = false)
    private List<SysRole> roleList;
    /**
     * 用户对应部门相关信息
     * */
    @TableField(exist = false)
    private List<SysDept> deptList;
    /**
     * 用户对应岗位相关信息
     * */
    @TableField(exist = false)
    private List<SysPost> postList;
    /**
     * 是否业务系统用户 1:*0:*
     */
    @TableField(exist = false)
    private String isBusinessUser="0";
}
