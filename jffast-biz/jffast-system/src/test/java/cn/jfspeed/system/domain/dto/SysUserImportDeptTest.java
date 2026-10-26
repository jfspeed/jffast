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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.idev.excel.FastExcel;
import cn.jfspeed.common.core.config.JfFastSystemProperties;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.excel.util.ExcelUtil;
import cn.jfspeed.system.entity.SysDept;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.handler.SysUserImportTemplateHandler;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.service.SysDeptScopeQuery;
import cn.jfspeed.system.service.SysUserImportContext;
import cn.jfspeed.system.service.SysUserImportContextFactory;
import cn.jfspeed.system.service.SysUserService;

/**
 * 导入归属部门（CO-1）：上下文解析、逐行失败文案、绑定落库、模板列契约。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysUserImportDeptTest {

    private static final long IMPORTED_ID_BASE = 7000L;

    @Mock
    private SysUserMapper baseMapper;
    @Mock
    private SysUserDeptMapper sysUserDeptMapper;
    @Mock
    private JfFastSystemProperties jfFastSystemProperties;

    private SysUserService service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysUserDept.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new SysUserService());
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "sysUserDeptMapper", sysUserDeptMapper);
        ReflectionTestUtils.setField(service, "jfFastSystemProperties", jfFastSystemProperties);
        when(jfFastSystemProperties.getInitPassword()).thenReturn("Init@123");
        when(baseMapper.selectList(any())).thenReturn(Collections.emptyList());
        doAnswer(invocation -> {
            Collection<SysUser> users = invocation.getArgument(0);
            long nextId = IMPORTED_ID_BASE;
            for (SysUser user : users) {
                user.setId(++nextId);
            }
            return true;
        }).when(service).saveBatch(anyList(), anyInt());
    }

    // 姓名（real_name）是 NOT NULL 列：导入时缺姓名会被逐行拒绝（见 SysUserService#importUsers），
    // 所以默认带上姓名，让本类用例聚焦「归属部门」行为。
    private static SysUserImportExcel row(String userName, String deptText) {
        return row(userName, userName + "姓名", deptText);
    }

    /** 姓名为空的行（NOT NULL 列缺失），用于逐行失败校验 */
    private static SysUserImportExcel rowWithoutRealName(String userName, String deptText) {
        return row(userName, null, deptText);
    }

    private static SysUserImportExcel row(String userName, String realName, String deptText) {
        SysUserImportExcel row = new SysUserImportExcel();
        row.setUserName(userName);
        row.setRealName(realName);
        row.setDeptText(deptText);
        return row;
    }

    private static SysUserImportContext context(boolean superAdmin, Map<Long, String> names) {
        Map<String, Long> uniqueIdByName = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        names.forEach((id, name) -> {
            Long previous = uniqueIdByName.put(name, id);
            if (previous != null && !previous.equals(id)) {
                ambiguous.add(name);
            }
        });
        return new SysUserImportContext(superAdmin, names, ambiguous, uniqueIdByName);
    }

    @Test
    @DisplayName("非超管留空归属：逐行失败，不落库")
    void blankDeptFailsForNonSuperAdmin() {
        SysUserImportContext ctx = context(false, Map.of(10L, "技术部"));

        SysUserImportResult result = service.importUsers(List.of(row("imp_blank", null)), 0, ctx);

        assertEquals(1, result.getTotal());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertTrue(result.getFailures().get(0).contains("归属部门不能为空"));
        verify(service, never()).saveBatch(anyList(), anyInt());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("超管留空归属：建号成功，不写绑定")
    void blankDeptSkipsBindingForSuperAdmin() {
        SysUserImportContext ctx = context(true, Map.of(10L, "技术部"));

        SysUserImportResult result = service.importUsers(List.of(row("imp_super", " ")), 0, ctx);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        ArgumentCaptor<Collection<SysUser>> captor = userCaptor();
        verify(service).saveBatch(captor.capture(), anyInt());
        assertNull(captor.getValue().iterator().next().getDeptId());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("按部门名称导入：写入 deptId 并落 1 条绑定")
    void deptNameResolvesAndWritesBinding() {
        SysUserImportContext ctx = context(false, Map.of(10L, "技术部"));

        SysUserImportResult result = service.importUsers(List.of(row("imp_name", "技术部")), 0, ctx);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<Collection<SysUser>> userCaptor = userCaptor();
        verify(service).saveBatch(userCaptor.capture(), anyInt());
        SysUser saved = userCaptor.getValue().iterator().next();
        assertEquals(10L, saved.getDeptId());
        assertNotNull(saved.getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SysUserDept>> bindings = ArgumentCaptor.forClass(List.class);
        verify(sysUserDeptMapper).insertBatch(bindings.capture());
        assertEquals(1, bindings.getValue().size());
        assertEquals(saved.getId(), bindings.getValue().get(0).getUserId());
        assertEquals(10L, bindings.getValue().get(0).getDeptId());
    }

    @Test
    @DisplayName("按部门 ID 导入：trim 后识别纯数字")
    void deptIdWithSpacesResolves() {
        SysUserImportContext ctx = context(false, Map.of(20L, "研发部"));

        SysUserImportResult result = service.importUsers(List.of(row("imp_id", " 20 ")), 0, ctx);

        assertEquals(1, result.getSuccessCount());
        ArgumentCaptor<Collection<SysUser>> captor = userCaptor();
        verify(service).saveBatch(captor.capture(), anyInt());
        assertEquals(20L, captor.getValue().iterator().next().getDeptId());
    }

    @Test
    @DisplayName("部门 ID 溢出 Long：格式错误文案")
    void overflowingDeptIdFailsRow() {
        SysUserImportContext ctx = context(false, Map.of(20L, "研发部"));

        SysUserImportResult result = service.importUsers(
                List.of(row("imp_overflow", "99999999999999999999")), 0, ctx);

        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getFailures().get(0).contains("归属部门ID格式不正确"));
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("部门不可见/不存在：不存在或无权使用文案")
    void unknownDeptFailsRow() {
        SysUserImportContext ctx = context(false, Map.of(10L, "技术部"));

        SysUserImportResult byName = service.importUsers(List.of(row("imp_unknown", "市场部")), 0, ctx);
        SysUserImportResult byId = service.importUsers(List.of(row("imp_unknown_id", "99")), 0, ctx);

        assertTrue(byName.getFailures().get(0).contains("部门不存在或无权使用该部门"));
        assertTrue(byId.getFailures().get(0).contains("部门不存在或无权使用该部门"));
    }

    @Test
    @DisplayName("部门名称重名：要求填写部门 ID")
    void ambiguousDeptNameFailsRow() {
        SysUserImportContext ctx = context(false, Map.of(10L, "重名部", 11L, "重名部"));

        SysUserImportResult result = service.importUsers(List.of(row("imp_ambiguous", "重名部")), 0, ctx);

        assertTrue(result.getFailures().get(0).contains("部门名称重复，请填写部门ID"));
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("部门名称区分大小写与首尾空白")
    void deptNameMatchingIsTrimmedAndCaseSensitive() {
        SysUserImportContext ctx = context(false, Map.of(10L, "Tech"));

        assertEquals(10L, ctx.resolveDeptId(" Tech ").deptId());
        assertEquals(SysUserImportContext.DeptResolutionStatus.NOT_FOUND,
                ctx.resolveDeptId("tech").status());
    }

    @Test
    @DisplayName("混合批次：成功行落库，失败行计入 failures")
    void mixedRowsKeepCounts() {
        SysUserImportContext ctx = context(false, Map.of(10L, "技术部"));

        SysUserImportResult result = service.importUsers(
                List.of(row("imp_ok", "10"), row("imp_fail", null)), 5, ctx);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        // rowOffset=5 表示前 5 行已处理，本批第 2 行即表格第 8 行
        assertTrue(result.getFailures().get(0).startsWith("第8行："));
        verify(service, times(1)).saveBatch(anyList(), anyInt());
    }

    @Test
    @DisplayName("数字单元格：17 位部门 ID 不得被截断后绑定到相邻部门")
    void numericCellDoesNotSilentlyBindAnotherDept() throws IOException {
        // 2^53+1 在 Excel 中落为 2^53，若按数字转换后直接当 ID，会命中相邻部门
        long exact = 9007199254740993L;
        long rounded = 9007199254740992L;
        byte[] workbook = workbookWithNumericDept("imp_numeric", exact);
        List<SysUserImportExcel> rows = ExcelUtil.read(new ByteArrayInputStream(workbook),
                SysUserImportExcel.class);
        assertEquals(1, rows.size());

        SysUserImportContext ctx = context(false, Map.of(rounded, "相邻部门", exact, "精确部门"));
        SysUserImportResult result = service.importUsers(rows, 0, ctx);

        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        verify(service, never()).saveBatch(anyList(), anyInt());
        verify(sysUserDeptMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("姓名为空：逐行失败，同批其他行照常落库")
    void blankRealNameFailsRowOnly() {
        // real_name 是 NOT NULL 列：缺姓名若不拦，insert 会触发 NOT NULL 违例把整批 saveBatch 回滚，
        // 一个空单元格废掉整个文件，且失败明细里看不到是哪一行的问题。
        SysUserImportContext ctx = context(false, Map.of(10L, "技术部"));

        SysUserImportResult result = service.importUsers(
                List.of(rowWithoutRealName("imp_noname", "技术部"), row("imp_ok", "技术部")), 0, ctx);

        assertEquals(2, result.getTotal());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertTrue(result.getFailures().get(0).contains("姓名不能为空"));
        verify(service, times(1)).saveBatch(anyList(), anyInt());
    }

    @Test
    @DisplayName("导入模板：6 列、第 6 列文本格式 + 表头填写说明")
    void importTemplateHasSixColumnsWithTextFormat() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.export(out, List.of(), SysUserImportExcel.class, new SysUserImportTemplateHandler());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertEquals(0, sheet.getLastRowNum());
            Row head = sheet.getRow(0);
            assertEquals(6, head.getLastCellNum());
            assertEquals("用户名", head.getCell(0).getStringCellValue());
            assertEquals("归属部门", head.getCell(5).getStringCellValue());
            CellStyle deptColumnStyle = sheet.getColumnStyle(5);
            assertNotNull(deptColumnStyle);
            assertEquals(49, deptColumnStyle.getDataFormat());
            assertNotNull(head.getCell(5).getCellComment());
            // 姓名是 NOT NULL 列，表头必须写明必填，否则用户填完才被拒
            assertNotNull(head.getCell(1).getCellComment());
        }
    }

    @Test
    @DisplayName("导出模型保持 5 列（不含归属部门）")
    void exportModelKeepsFiveColumns() {
        SysUserExcel excel = new SysUserExcel();
        excel.setUserName("exp_user");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExcelUtil.export(out, List.of(excel), SysUserExcel.class);

        List<String> headers = headers(out.toByteArray());
        assertEquals(5, headers.size());
        assertEquals("用户名", headers.get(0));
        assertFalse(headers.contains("归属部门"));
    }

    @Test
    @DisplayName("上下文工厂：请求线程只查一次可见部门，并识别重名")
    void factoryLoadsVisibleDeptsOnceAndMarksAmbiguity() {
        SysDeptScopeQuery scopeQuery = mock(SysDeptScopeQuery.class);
        SysDept a = dept(1L, "重名部");
        SysDept b = dept(2L, "重名部");
        SysDept c = dept(3L, "技术部");
        when(scopeQuery.listVisibleDepts()).thenReturn(List.of(a, b, c));
        SysUserImportContextFactory factory = new SysUserImportContextFactory();
        ReflectionTestUtils.setField(factory, "sysDeptScopeQuery", scopeQuery);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            SysUser operator = new SysUser();
            operator.setId(1L);
            login.when(LoginUtil::getLoginSysUser).thenReturn(operator);
            login.when(LoginUtil::isSuperAdmin).thenReturn(false);

            SysUserImportContext ctx = factory.create();

            assertFalse(ctx.superAdmin());
            assertTrue(ctx.ambiguousDeptNames().contains("重名部"));
            assertEquals(3L, ctx.uniqueIdByName().get("技术部"));
            assertEquals(3, ctx.visibleDeptNames().size());
            verify(scopeQuery, times(1)).listVisibleDepts();
            assertEquals(SysUserImportContext.DeptResolutionStatus.AMBIGUOUS,
                    ctx.resolveDeptId("重名部").status());
        }
    }

    @Test
    @DisplayName("上下文工厂：可见部门名称为 NULL 时仍可构建上下文并按 ID 解析")
    void factoryToleratesNullDeptName() {
        SysDeptScopeQuery scopeQuery = mock(SysDeptScopeQuery.class);
        SysDept unnamed = dept(7L, null);
        when(scopeQuery.listVisibleDepts()).thenReturn(List.of(unnamed));
        SysUserImportContextFactory factory = new SysUserImportContextFactory();
        ReflectionTestUtils.setField(factory, "sysDeptScopeQuery", scopeQuery);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            SysUser operator = new SysUser();
            operator.setId(1L);
            login.when(LoginUtil::getLoginSysUser).thenReturn(operator);
            login.when(LoginUtil::isSuperAdmin).thenReturn(false);

            SysUserImportContext ctx = factory.create();

            assertEquals("", ctx.visibleDeptNames().get(7L));
            assertTrue(ctx.uniqueIdByName().isEmpty());
            SysUserImportContext.DeptResolution resolution = ctx.resolveDeptId("7");
            assertEquals(SysUserImportContext.DeptResolutionStatus.OK, resolution.status());
            assertEquals(7L, resolution.deptId());
        }
    }

    @Test
    @DisplayName("上下文工厂：无登录态时拒绝（403），不查询部门")
    void factoryRejectsMissingLogin() {
        SysDeptScopeQuery scopeQuery = mock(SysDeptScopeQuery.class);
        SysUserImportContextFactory factory = new SysUserImportContextFactory();
        ReflectionTestUtils.setField(factory, "sysDeptScopeQuery", scopeQuery);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginSysUser).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class, factory::create);

            assertEquals(403, ex.getCode());
            verify(scopeQuery, never()).listVisibleDepts();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Collection<SysUser>> userCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    private static SysDept dept(Long id, String name) {
        SysDept dept = new SysDept();
        dept.setId(id);
        dept.setDeptName(name);
        return dept;
    }

    private static byte[] workbookWithNumericDept(String userName, double deptValue) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            String[] headers = {"用户名", "姓名", "手机号", "邮箱", "状态", "归属部门"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                head.createCell(i).setCellValue(headers[i]);
            }
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue(userName);
            // 姓名必填（NOT NULL 列）：不填会先被姓名校验拦下，测不到「数字单元格不误绑相邻部门」本身
            data.createCell(1).setCellValue(userName + "姓名");
            data.createCell(5).setCellValue(deptValue);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private static List<String> headers(byte[] workbook) {
        List<Map<Integer, String>> rows = FastExcel.read(new ByteArrayInputStream(workbook))
                .headRowNumber(0).sheet().doReadSync();
        List<String> headers = new ArrayList<>();
        rows.get(0).forEach((index, value) -> headers.add(index, value));
        return headers;
    }
}
