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

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 用户导入/导出 Excel 模型
 *
 * @author jffast
 */
@Data
public class SysUserExcel {

    @ExcelProperty("用户名")
    private String userName;

    @ExcelProperty("姓名")
    private String realName;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    /**
     * 状态：正常 / 停用（导入时留空按正常处理）
     */
    @ExcelProperty("状态")
    private String stateText;
}
