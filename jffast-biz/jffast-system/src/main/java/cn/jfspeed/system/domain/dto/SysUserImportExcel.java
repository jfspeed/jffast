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
import cn.idev.excel.annotation.write.style.ColumnWidth;
import cn.idev.excel.annotation.write.style.ContentStyle;
import lombok.Data;

/**
 * 用户导入 Excel 模型（6 列）。
 * <p>
 * 与导出模型 {@link SysUserExcel} 分开：导出固定 5 列，导入多一列「归属部门」。
 * 第 6 列单元格默认文本格式，避免 17 位部门 ID 被 Excel 按数字精度截断。
 *
 * @author jffast
 */
@Data
public class SysUserImportExcel {

    @ExcelProperty(value = "用户名", index = 0)
    private String userName;

    @ExcelProperty(value = "姓名", index = 1)
    private String realName;

    @ExcelProperty(value = "手机号", index = 2)
    private String phone;

    @ExcelProperty(value = "邮箱", index = 3)
    private String email;

    /**
     * 状态：正常 / 停用（导入时留空按正常处理）
     */
    @ExcelProperty(value = "状态", index = 4)
    private String stateText;

    /**
     * 归属部门：纯数字按部门 ID，否则按部门名称精确匹配；非超管必填
     */
    @ExcelProperty(value = "归属部门", index = 5)
    @ColumnWidth(28)
    @ContentStyle(dataFormat = 49)
    private String deptText;
}
