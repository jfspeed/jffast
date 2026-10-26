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
import cn.jfspeed.common.data.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    @TableField("name")
    private String name;

    @TableField("parent_id")
    private Long parentId;

    @TableField("sort")
    private Integer sort;

    @TableField("url")
    private String url;

    @TableField("path")
    private String path;

    @TableField("type")
    private String type;

    @TableField("state")
    private Integer state;

    @TableField("perms")
    private String perms;

    @TableField("visible")
    private Boolean visible;

    @TableField("icon")
    private String icon;

    @TableField("remark")
    private String remark;

    @TableField("active_menu")
    private String activeMenu;

    @TableField(exist = false)
    private List<SysMenu> children = new ArrayList<>();
}
