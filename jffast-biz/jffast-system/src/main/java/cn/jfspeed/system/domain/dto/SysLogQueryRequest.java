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

import cn.jfspeed.common.core.domain.dto.BaseQueryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jffast
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "日志查询请求")
public class SysLogQueryRequest extends BaseQueryDTO {
    @Schema(description = "操作IP")
    private String ip;

    @Schema(description = "日志标题")
    private String title;

    @Schema(description = "日志内容")
    private String info;

    @Schema(description = "请求方法")
    private String method;

    @Schema(description = "创建者用户名")
    private String createByName;

    @Schema(description = "日志状态：1-成功，2-失败")
    private String state;

    @Schema(description = "日志级别：info/warning/error")
    private String level;

    @Schema(description = "请求URI")
    private String requestUri;

    public SysLogQueryRequest(String ip, String title, String method) {
        this.ip = ip;
        this.title = title;
        this.method = method;
    }

    public SysLogQueryRequest() {}
}
