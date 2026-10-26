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
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_log")
@Schema(description = "系统日志")
public class SysLog extends BaseEntity {

    @TableField("tenant_id")
    @Schema(description = "租户ID")
    private Long tenantId;

    @TableField("ip")
    @Schema(description = "操作IP")
    private String ip;

    @TableField("title")
    @Schema(description = "日志标题")
    private String title;

    @TableField("method")
    @Schema(description = "请求方法")
    private String method;

    @TableField("info")
    @Schema(description = "日志内容")
    private String info;

    @TableField("state")
    @Schema(description = "日志状态：1-成功，2-失败")
    private String state;

    @TableField("level")
    @Schema(description = "日志级别：info/warning/error")
    private String level;

    @TableField("request_uri")
    @Schema(description = "请求URI")
    private String requestUri;

    @TableField("duration")
    @Schema(description = "执行时长（毫秒）")
    private Long duration;

    public SysLog(String ip, String title, String method) {
        this.ip = ip;
        this.title = title;
        this.method = method;
    }

    public SysLog() {
    }
}
