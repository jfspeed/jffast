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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import cn.jfspeed.common.data.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_safe")
public class SysSafe extends BaseEntity {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("pwd_cycle")
    private Integer pwdCycle;

    @TableField("pwd_login_limit")
    private Integer pwdLoginLimit;

    @TableField("idle_time_setting")
    private Integer idleTimeSetting;

}
