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
package cn.jfspeed.excel.util;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Excel 导入导出单元测试（内存流往返，不依赖 Servlet 环境）。
 *
 * @author jffast
 */
class ExcelUtilTest {

    @Test
    @DisplayName("导出到输出流后能原样读回")
    void shouldRoundTripThroughExcel() {
        DemoRow row = new DemoRow();
        row.setName("jffast");
        row.setCount(3);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.export(out, List.of(row), DemoRow.class);
        assertTrue(out.size() > 0, "导出内容不应为空");

        List<DemoRow> readBack = ExcelUtil.read(new ByteArrayInputStream(out.toByteArray()), DemoRow.class);
        assertEquals(1, readBack.size());
        assertEquals("jffast", readBack.get(0).getName());
        assertEquals(Integer.valueOf(3), readBack.get(0).getCount());
    }

    @Test
    @DisplayName("导出空集合不报错")
    void shouldExportEmptyList() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.export(out, List.of(), DemoRow.class);
        assertTrue(out.size() > 0, "空集合也会写出表头");
    }

    /** 测试用行模型 */
    @Data
    public static class DemoRow {

        @ExcelProperty("名称")
        private String name;

        @ExcelProperty("数量")
        private Integer count;
    }
}
