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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.idev.excel.FastExcel;
import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.write.builder.ExcelWriterBuilder;
import cn.idev.excel.write.handler.WriteHandler;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import cn.jfspeed.excel.handler.SensitiveMaskCellWriteHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class ExcelUtil {

    public static <T> List<T> read(InputStream inputStream, Class<T> clazz) {
        List<T> list = new ArrayList<>();
        FastExcel.read(inputStream, clazz, new AnalysisEventListener<T>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                list.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();
        return list;
    }

    public static <T> void export(HttpServletResponse response, String fileName, List<T> data, Class<T> clazz) {
        export(response, fileName, data, clazz, new WriteHandler[0]);
    }

    public static <T> void export(HttpServletResponse response, String fileName, List<T> data, Class<T> clazz,
                                  WriteHandler... extraHandlers) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("\\+", "%20"));
            export(response.getOutputStream(), data, clazz, extraHandlers);
        } catch (IOException e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败", e);
        }
    }

    /**
     * 导出到任意输出流（异步导出落盘等场景）
     */
    public static <T> void export(OutputStream outputStream, List<T> data, Class<T> clazz) {
        export(outputStream, data, clazz, new WriteHandler[0]);
    }

    /**
     * 导出到任意输出流，extraHandlers 用于按场景追加写入处理器（如导入模板的表头说明）
     */
    public static <T> void export(OutputStream outputStream, List<T> data, Class<T> clazz,
                                  WriteHandler... extraHandlers) {
        ExcelWriterBuilder builder = FastExcel.write(outputStream, clazz)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .registerWriteHandler(new SensitiveMaskCellWriteHandler());
        if (extraHandlers != null) {
            for (WriteHandler handler : extraHandlers) {
                if (handler != null) {
                    builder.registerWriteHandler(handler);
                }
            }
        }
        builder.sheet("Sheet1").doWrite(data);
    }
}