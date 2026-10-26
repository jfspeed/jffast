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
package cn.jfspeed.demo.domain.dto;

import java.math.BigDecimal;

import cn.idev.excel.annotation.ExcelProperty;
import cn.jfspeed.common.core.annotation.SensitiveMask;
import lombok.Data;

/**
 * demo_example Excel 导入导出 DTO。
 * <p>
 * 用于演示 jffast-starter-excel 的导入导出能力：
 * 字段通过 {@link ExcelProperty} 标注表格列名，手机号标注 {@link SensitiveMask} 在导出时自动脱敏。
 *
 * @author jffast
 */
@Data
public class DemoExampleExcel {

    @ExcelProperty("标题")
    private String title;

    @ExcelProperty("类型")
    private String type;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("优先级")
    private Integer priority;

    @ExcelProperty("评分")
    private BigDecimal score;

    @ExcelProperty("数量")
    private Integer quantity;

    @ExcelProperty("价格")
    private BigDecimal price;

    @ExcelProperty("推荐")
    private Integer isRecommend;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("手机号")
    @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 3, suffixKeep = 4, maskLength = 4)
    private String phone;

    @ExcelProperty("备注")
    private String remark;
}