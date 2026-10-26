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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统参数配置实体
 *
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
@Schema(description = "系统参数配置")
public class SysConfig extends BaseEntity {

    @TableField("config_name")
    @Schema(description = "参数名称")
    private String configName;

    @TableField("config_key")
    @Schema(description = "参数键名")
    private String configKey;

    @TableField("config_value")
    @Schema(description = "参数键值")
    private String configValue;

    @TableField("system_builtin")
    @Schema(description = "系统内置：0-否，1-是（内置不可删除）")
    private Integer systemBuiltin;

    @TableField("remark")
    @Schema(description = "备注")
    private String remark;
}
