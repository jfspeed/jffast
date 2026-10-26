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

import java.time.LocalDateTime;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 系统日志 Excel 导出 DTO。
 * <p>
 * 操作/登录日志共用 {@code sys_log}，导出时按查询条件过滤；操作人名称由
 * {@code create_by} 反查用户表后填入。
 *
 * @author jffast
 */
@Data
public class SysLogExcel {

    @ExcelProperty("日志标题")
    private String title;

    @ExcelProperty("操作人")
    private String createByName;

    @ExcelProperty("请求方式")
    private String method;

    @ExcelProperty("请求URI")
    private String requestUri;

    @ExcelProperty("操作IP")
    private String ip;

    @ExcelProperty("日志状态")
    private String stateText;

    @ExcelProperty("日志级别")
    private String level;

    @ExcelProperty("执行时长(毫秒)")
    private Long duration;

    @ExcelProperty("日志内容")
    private String info;

    @ExcelProperty("操作时间")
    private LocalDateTime createTime;
}
