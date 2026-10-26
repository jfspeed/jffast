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
package cn.jfspeed.excel.handler;

import java.lang.reflect.Field;

import org.apache.poi.ss.usermodel.Cell;

import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteTableHolder;
import cn.jfspeed.common.core.annotation.SensitiveMask;
import cn.jfspeed.common.core.util.SensitiveMaskProcessor;

/**
 * Excel 导出时识别 {@link SensitiveMask} 注解并进行字段脱敏。
 *
 * @author jffast
 */
public class SensitiveMaskCellWriteHandler implements CellWriteHandler {

    @Override
    public void afterCellDataConverted(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder,
                                       WriteCellData<?> cellData, Cell cell, Head head,
                                       Integer relativeRowIndex, Boolean isHead) {
        if (isHead || cellData == null || head == null) {
            return;
        }

        Field field = head.getField();
        if (field == null) {
            return;
        }

        SensitiveMask annotation = field.getAnnotation(SensitiveMask.class);
        if (annotation == null) {
            return;
        }

        Object data = cellData.getData();
        if (data instanceof String) {
            String masked = SensitiveMaskProcessor.mask((String) data, annotation);
            @SuppressWarnings("unchecked")
            WriteCellData<Object> rawCellData = (WriteCellData<Object>) cellData;
            rawCellData.setData(masked);
        }
    }
}
