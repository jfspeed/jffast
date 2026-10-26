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
package cn.jfspeed.system.handler;

import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import cn.idev.excel.metadata.Head;
import cn.idev.excel.metadata.data.WriteCellData;
import cn.idev.excel.write.handler.CellWriteHandler;
import cn.idev.excel.write.handler.SheetWriteHandler;
import cn.idev.excel.write.metadata.holder.WriteSheetHolder;
import cn.idev.excel.write.metadata.holder.WriteTableHolder;
import cn.idev.excel.write.metadata.holder.WriteWorkbookHolder;

/**
 * 用户导入模板的「归属部门」列增强：整列默认文本格式（防长数字 ID 被表格软件按精度截断），
 * 并在表头挂填写说明。
 *
 * @author jffast
 */
public class SysUserImportTemplateHandler implements SheetWriteHandler, CellWriteHandler {

    /** 归属部门列下标（0 基，与 SysUserImportExcel#deptText 的 index 一致） */
    private static final int DEPT_COLUMN_INDEX = 5;

    /** 姓名列下标（0 基，与 SysUserImportExcel#realName 的 index 一致） */
    private static final int REAL_NAME_COLUMN_INDEX = 1;

    private static final String DEPT_HEADER_COMMENT =
            "非超级管理员必填：可填部门名称或部门ID，名称重复时请填写部门ID。"
                    + "请以文本格式粘贴长数字ID，避免被表格软件截断；留空对超级管理员表示不设归属部门。";

    private static final String REAL_NAME_HEADER_COMMENT =
            "必填：用户真实姓名（库字段非空），最长 20 个字符。留空该行会被逐行标记为失败。";

    @Override
    public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
        Sheet sheet = writeSheetHolder.getSheet();
        Workbook workbook = writeWorkbookHolder.getWorkbook();
        CellStyle textStyle = workbook.createCellStyle();
        textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
        sheet.setDefaultColumnStyle(DEPT_COLUMN_INDEX, textStyle);
    }

    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder, WriteTableHolder writeTableHolder,
                                 List<WriteCellData<?>> cellDataList, Cell cell, Head head,
                                 Integer relativeRowIndex, Boolean isHead) {
        if (!Boolean.TRUE.equals(isHead) || cell == null) {
            return;
        }
        String commentText = headerCommentFor(cell.getColumnIndex());
        if (commentText == null) {
            return;
        }
        Sheet sheet = writeSheetHolder.getSheet();
        Workbook workbook = sheet.getWorkbook();
        CreationHelper creationHelper = workbook.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();
        ClientAnchor anchor = creationHelper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 2);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(creationHelper.createRichTextString(commentText));
        comment.setAuthor("jffast");
        cell.setCellComment(comment);
    }

    /** 该列是否有表头填写说明；无则返回 null */
    private static String headerCommentFor(int columnIndex) {
        if (columnIndex == DEPT_COLUMN_INDEX) {
            return DEPT_HEADER_COMMENT;
        }
        if (columnIndex == REAL_NAME_COLUMN_INDEX) {
            return REAL_NAME_HEADER_COMMENT;
        }
        return null;
    }
}
