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
package cn.jfspeed.common.core.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.stereotype.Component;

import cn.hutool.core.util.StrUtil;

import cn.jfspeed.common.core.util.generator.IdUtils;
import jakarta.annotation.Resource;

/**
 * @author jffast
 */
@Component
public class CodeGenerator {

    @Resource
    private DataSource dataSource;

    /**
     * 仅供 {@link CodeGeneratorCLI} 在脱离 Spring 容器时注入数据源；
     * 应用内一律走 {@code @Resource} 自动装配，不要手动调用。
     */
    void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<TableInfo> listTables() {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");
                    tables.add(new TableInfo(name, comment != null ? comment : name));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list tables", e);
        }
        return tables;
    }

    public List<ColumnInfo> getColumns(String tableName) {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getColumns(catalog, null, tableName, null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    String comment = rs.getString("REMARKS");
                    columns.add(new ColumnInfo(name, type, size, comment != null ? comment : ""));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get columns for table: " + tableName, e);
        }
        return columns;
    }

    public GeneratedCode generate(String tableName, String packageName, String author, boolean includeDto) {
        return generate(tableName, packageName, author, includeDto, "crud", null, null, null, null, null);
    }

    public GeneratedCode generate(String tableName, String packageName, String author, boolean includeDto,
                                   String genType, String parentField, String nameField,
                                   String childTableName, String childForeignKey, String childPackageName) {
        List<ColumnInfo> columns = getColumns(tableName);
        String tableComment = getTableComment(tableName);

        String className = toCamelCase(tableName, true);
        String basePkg = "cn.jfspeed";
        if (packageName == null || packageName.isBlank()) {
            // 包名缺省会拼出非法包 `cn.jfspeed..entity`（双点）；落回 demo，
            // 与仓内 jffast-demo 模块及其 `cn.jfspeed.demo` 包约定一致
            packageName = "demo";
        }
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);
        String url = "/" + frontendModule + "/" + pageName;

        // 自动检测树形字段：如果 genType 为 crud 但表中有 parent_id 等字段，自动切换为树表模式
        if ("crud".equals(genType)) {
            String detectedParentField = detectTreeField(columns);
            if (detectedParentField != null) {
                genType = "tree";
                if (parentField == null || parentField.isEmpty()) {
                    parentField = detectedParentField;
                }
            }
        }

        String entityCode;
        String serviceCode;
        String controllerCode;
        String frontendIndexCode;
        String frontendSaveCode;

        String frontendApiCode;

        if ("tree".equals(genType)) {
            String pField = (parentField != null && !parentField.isEmpty()) ? parentField : "parent_id";
            String nField = (nameField != null && !nameField.isEmpty()) ? nameField : detectNameField(columns);
            entityCode = generateTreeEntity(tableName, className, columns, basePkg, packageName, author, tableComment, pField, nField);
            serviceCode = generateTreeService(className, basePkg, packageName, author, tableComment, pField, nField);
            controllerCode = generateTreeController(className, tableName, basePkg, packageName, author, tableComment, pField);
            frontendApiCode = generateTreeFrontendApi(className, url, frontendModule, author, tableComment);
            frontendIndexCode = generateTreeFrontendIndexPage(className, pageName, columns, frontendModule, tableComment, author, pField, nField);
            frontendSaveCode = generateTreeFrontendSavePage(className, pageName, columns, frontendModule, tableComment, author, pField, nField);
        } else if ("masterDetail".equals(genType) && childTableName != null && !childTableName.isEmpty()) {
            entityCode = generateEntity(tableName, className, columns, basePkg, packageName, author, tableComment);
            serviceCode = generateMasterService(className, basePkg, packageName, author, tableComment,
                    childTableName, childForeignKey, childPackageName);
            controllerCode = generateMasterController(className, tableName, basePkg, packageName, author, tableComment,
                    childTableName, childForeignKey, childPackageName);
            frontendApiCode = generateFrontendApi(className, url, frontendModule, author, tableComment);
            frontendIndexCode = generateMasterDetailFrontendIndexPage(className, pageName, columns, frontendModule, tableComment, author,
                    childTableName, childForeignKey, childPackageName);
            frontendSaveCode = generateFrontendSavePage(className, pageName, columns, frontendModule, tableComment, author);
        } else {
            entityCode = generateEntity(tableName, className, columns, basePkg, packageName, author, tableComment);
            serviceCode = generateService(className, basePkg, packageName, author, tableComment);
            controllerCode = generateController(className, tableName, basePkg, packageName, author, tableComment);
            frontendApiCode = generateFrontendApi(className, url, frontendModule, author, tableComment);
            frontendIndexCode = generateFrontendIndexPage(className, pageName, columns, frontendModule, tableComment, author);
            frontendSaveCode = generateFrontendSavePage(className, pageName, columns, frontendModule, tableComment, author);
        }

        String childEntityCode = null, childMapperCode = null, childServiceCode = null;
        String childControllerCode = null, childFrontendApiCode = null;
        String childFrontendIndexCode = null, childFrontendSaveCode = null;

        if ("masterDetail".equals(genType) && childTableName != null && !childTableName.isEmpty()) {
            String childPkg = (childPackageName != null && !childPackageName.isEmpty()) ? childPackageName : packageName;
            List<ColumnInfo> childColumns = getColumns(childTableName);
            String childTableComment = getTableComment(childTableName);
            String childClassName = toCamelCase(childTableName, true);
            String childPageName = uncapitalize(childClassName);
            String childUrl = "/" + getFrontendModule(childPkg) + "/" + childPageName;

            childEntityCode = generateEntity(childTableName, childClassName, childColumns, basePkg, childPkg, author, childTableComment);
            childMapperCode = generateMapper(childClassName, basePkg, childPkg, author, childTableComment);
            childServiceCode = generateService(childClassName, basePkg, childPkg, author, childTableComment);
            childControllerCode = generateController(childClassName, childTableName, basePkg, childPkg, author, childTableComment);
            childFrontendApiCode = generateFrontendApi(childClassName, childUrl, getFrontendModule(childPkg), author, childTableComment);
            childFrontendIndexCode = generateFrontendIndexPage(childClassName, childPageName, childColumns, getFrontendModule(childPkg), childTableComment, author);
            childFrontendSaveCode = generateFrontendSavePage(childClassName, childPageName, childColumns, getFrontendModule(childPkg), childTableComment, author);
        }

        return new GeneratedCode(
                entityCode,
                generateMapper(className, basePkg, packageName, author, tableComment),
                serviceCode,
                controllerCode,
                generateSql(tableName, columns),
                frontendApiCode,
                frontendIndexCode,
                frontendSaveCode,
                generateFrontendRouter(className, pageName, frontendModule, tableComment),
                // dtoRequest 无条件生成：Controller 模板的 create/update 已依赖它，
                // 若随 includeDto 关闭会生成无法编译的 Controller。
                generateDtoRequest(className, columns, basePkg, packageName, author, tableComment),
                includeDto ? generateDtoQueryRequest(className, columns, basePkg, packageName, author, tableComment) : null,
                generateMenuSql(tableName, pageName, frontendModule, tableComment),
                childEntityCode, childMapperCode, childServiceCode,
                childControllerCode, childFrontendApiCode,
                childFrontendIndexCode, childFrontendSaveCode,
                genType
        );
    }

    private String detectNameField(List<ColumnInfo> columns) {
        for (ColumnInfo col : columns) {
            String lower = col.name.toLowerCase();
            if (lower.equals("name") || lower.equals("title") || lower.equals("label")) {
                return col.name;
            }
        }
        for (ColumnInfo col : columns) {
            String lower = col.name.toLowerCase();
            if (lower.endsWith("_name") || lower.endsWith("_title") || lower.endsWith("_label")) {
                return col.name;
            }
        }
        return "name";
    }

    /**
     * 自动检测表是否为树形表，返回父节点字段名；如果不是树形表返回 null
     * 支持检测的字段：parent_id, pid, father_id, parent_id, parentCode, parent_code 等
     */
    public String detectTreeField(List<ColumnInfo> columns) {
        Set<String> treeFieldPatterns = Set.of(
                "parent_id", "pid", "father_id", "parentid", "parent_code", "parentcode", "parent_fid"
        );
        for (ColumnInfo col : columns) {
            if (treeFieldPatterns.contains(col.name.toLowerCase())) {
                return col.name;
            }
        }
        return null;
    }

    /**
     * 检测表是否为树形表（便捷方法）
     */
    public boolean isTreeTable(String tableName) {
        List<ColumnInfo> columns = getColumns(tableName);
        return detectTreeField(columns) != null;
    }

    private String getFrontendModule(String packageName) {
        if (packageName.contains(".")) {
            return packageName.substring(packageName.lastIndexOf('.') + 1);
        }
        return packageName;
    }

    private String getTableComment(String tableName) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            try (ResultSet rs = meta.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) {
                    String comment = rs.getString("REMARKS");
                    return comment != null && !comment.isEmpty() ? comment : tableName;
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return tableName;
    }

    private String generateEntity(String tableName, String className, List<ColumnInfo> columns,
                                   String basePkg, String packageName, String author, String tableComment) {
        Set<String> columnNames = new java.util.HashSet<>();
        for (ColumnInfo col : columns) {
            columnNames.add(col.name);
        }

        boolean missingTenantId = !columnNames.contains("tenant_id");
        boolean missingDeptId = !columnNames.contains("dept_id");
        boolean missingCreateBy = !columnNames.contains("create_by");
        boolean missingCreateTime = !columnNames.contains("create_time");
        boolean missingUpdateBy = !columnNames.contains("update_by");
        boolean missingUpdateTime = !columnNames.contains("update_time");

        boolean hasIgnoreAnnotation = missingTenantId || missingDeptId || missingCreateBy
                || missingCreateTime || missingUpdateBy || missingUpdateTime;

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".").append(packageName).append(".entity;\n\n");
        sb.append("import com.baomidou.mybatisplus.annotation.TableName;\n");
        sb.append("import ").append(basePkg).append(".common.data.base.BaseEntity;\n");
        if (hasIgnoreAnnotation) {
            sb.append("import ").append(basePkg).append(".common.core.annotation.IgnoreBaseField;\n");
        }
        sb.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        sb.append("import lombok.Data;\n");
        sb.append("import lombok.EqualsAndHashCode;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(tableComment).append("实体类\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" */\n");
        sb.append("@Data\n");
        sb.append("@EqualsAndHashCode(callSuper = true)\n");
        sb.append("@TableName(\"").append(tableName).append("\")\n");
        sb.append("@Schema(description = \"").append(className).append("\")\n");
        if (hasIgnoreAnnotation) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (missingTenantId) parts.add("tenantId = true");
            if (missingDeptId) parts.add("deptId = true");
            if (missingCreateBy) parts.add("createBy = true");
            if (missingCreateTime) parts.add("createTime = true");
            if (missingUpdateBy) parts.add("updateBy = true");
            if (missingUpdateTime) parts.add("updateTime = true");
            sb.append("@IgnoreBaseField(").append(String.join(", ", parts)).append(")\n");
        }
        sb.append("public class ").append(className).append(" extends BaseEntity {\n\n");

        for (ColumnInfo col : columns) {
            if (isBaseColumn(col.name)) continue;
            String javaType = toJavaType(col.type);
            String fieldName = toCamelCase(col.name, false);

            sb.append("    @Schema(description = \"")
                    .append(escapeJavaLiteral(col.comment.isEmpty() ? col.name : col.comment)).append("\")\n");
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateMapper(String className, String basePkg, String packageName, String author, String tableComment) {
        return "/**\n" +
                " * " + tableComment + "数据访问层\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".mapper;\n\n" +
                "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n\n" +
                "public interface " + className + "Mapper extends BaseMapper<" + className + "> {\n" +
                "}\n";
    }

    private String generateService(String className, String basePkg, String packageName, String author, String tableComment) {
        return "/**\n" +
                " * " + tableComment + "服务类\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".service;\n\n" +
                "import cn.hutool.core.util.StrUtil;\n" +
                "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n" +
                "import " + basePkg + ".common.data.base.BaseService;\n" +
                "import " + basePkg + ".common.core.util.page.PageReq;\n" +
                "import " + basePkg + ".common.core.util.page.PageRes;\n" +
                "import " + basePkg + ".common.data.util.page.PageUtil;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".mapper." + className + "Mapper;\n" +
                "import org.springframework.stereotype.Service;\n\n" +
                "@Service\n" +
                "public class " + className + "Service extends BaseService<" + className + "Mapper, " + className + "> {\n\n" +
                "    public PageRes<" + className + "> page(" + className + " param, PageReq pageReq) {\n" +
                "        LambdaQueryWrapper<" + className + "> queryWrapper = new LambdaQueryWrapper<>();\n" +
                "        queryWrapper.orderByDesc(" + className + "::getCreateTime);\n" +
                "        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));\n" +
                "    }\n" +
                "}\n";
    }

    private String generateController(String className, String tableName, String basePkg, String packageName, String author, String tableComment) {
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);
        String url = "/" + frontendModule + "/" + pageName;
        String perm = frontendModule + ":" + pageName + ":";

        return "/**\n" +
                " * " + tableComment + "控制器\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".controller;\n\n" +
                "import " + basePkg + ".log.annotation.SLog;\n" +
                "import " + basePkg + ".security.annotation.RateLimit;\n" +
                "import " + basePkg + ".security.annotation.DistributedLock;\n" +
                "import " + basePkg + ".common.core.base.BaseConstant;\n" +
                "import " + basePkg + ".common.data.base.BaseController;\n" +
                "import " + basePkg + ".common.web.domain.dto.R;\n" +
                "import " + basePkg + ".common.core.util.page.PageReq;\n" +
                "import " + basePkg + ".common.core.util.page.PageRes;\n" +
                "import cn.dev33.satoken.annotation.SaCheckPermission;\n" +
                "import cn.hutool.core.bean.BeanUtil;\n" +
                "import jakarta.validation.Valid;\n" +
                "import " + basePkg + "." + packageName + ".domain.dto." + className + "Request;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".service." + className + "Service;\n" +
                "import io.swagger.v3.oas.annotations.Operation;\n" +
                "import io.swagger.v3.oas.annotations.tags.Tag;\n" +
                "import org.springdoc.core.annotations.ParameterObject;\n" +
                "import org.springframework.web.bind.annotation.*;\n\n" +
                "import java.util.List;\n\n" +
                "@Tag(name = \"" + className + "\")\n" +
                "@RestController\n" +
                "@RequestMapping(\"" + url + "\")\n" +
                "public class " + className + "Controller extends BaseController<" + className + "Service, " + className + "> {\n\n" +
                "    @GetMapping(\"/page\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"分页查询\")\n" +
                "    public R<PageRes<" + className + ">> page(@ModelAttribute " + className + " param, @ParameterObject PageReq pageReq) {\n" +
                "        return R.success(service.page(param, pageReq));\n" +
                "    }\n\n" +
                "    @GetMapping(\"/detail/{id}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"根据ID获取详情\")\n" +
                "    public R<" + className + "> detail(@PathVariable(\"id\") Long id) {\n" +
                "        return R.success(service.getById(id));\n" +
                "    }\n\n" +
                "    @PostMapping(\"/create\")\n" +
                "    @Operation(summary = \"新增\")\n" +
                "    @SLog(title = \"新增" + className + "\")\n" +
                "    @RateLimit(value = \"default\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "add\")\n" +
                "    public R<String> create(@RequestBody @Valid " + className + "Request req) {\n" +
                "        " + className + " entity = BeanUtil.copyProperties(req, " + className + ".class);\n" +
                "        service.save(entity);\n" +
                "        return R.successMsg(\"新增成功\");\n" +
                "    }\n\n" +
                "    @PutMapping(\"/update\")\n" +
                "    @Operation(summary = \"修改\")\n" +
                "    @SLog(title = \"修改" + className + "\")\n" +
                "    @DistributedLock(key = \"'" + perm + "' + #req.id\", expireTime = 10000)\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "update\")\n" +
                "    public R<String> update(@RequestBody @Valid " + className + "Request req) {\n" +
                "        " + className + " entity = BeanUtil.copyProperties(req, " + className + ".class);\n" +
                "        service.updateById(entity);\n" +
                "        return R.successMsg(\"修改成功\");\n" +
                "    }\n\n" +
                "    @DeleteMapping(\"/{id}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "delete\")\n" +
                "    @Operation(summary = \"删除\")\n" +
                "    @SLog(title = \"删除" + className + "\")\n" +
                "    public R<String> delete(@PathVariable(\"id\") Long id) {\n" +
                "        service.removeById(id);\n" +
                "        return R.successMsg(\"删除成功\");\n" +
                "    }\n\n" +
                "    @GetMapping(\"/all\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"获取全部\")\n" +
                "    public R<List<" + className + ">> all() {\n" +
                "        return R.success(service.list());\n" +
                "    }\n" +
                "}\n";
    }

    private String generateSql(String tableName, List<ColumnInfo> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");
        for (int i = 0; i < columns.size(); i++) {
            ColumnInfo col = columns.get(i);
            sb.append("    ").append(col.name).append(" ").append(col.type.toUpperCase());
            if (col.size > 0 && needsSize(col.type)) {
                sb.append("(").append(col.size).append(")");
            }
            if (col.name.equalsIgnoreCase("id")) {
                sb.append(" AUTO_INCREMENT PRIMARY KEY");
            }
            if (i < columns.size() - 1) sb.append(",");
            if (!col.comment.isEmpty()) sb.append(" COMMENT '").append(escapeSqlLiteral(col.comment)).append("'");
            sb.append("\n");
        }
        sb.append(");\n");
        return sb.toString();
    }

    private String generateFrontendApi(String className, String url, String frontendModule, String author, String tableComment) {
        String apiName = uncapitalize(className);
        return "/**\n" +
                " * " + tableComment + " API\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "const " + apiName + " = createCrud('" + url + "');\n\n" +
                "export default { " + apiName + " };\n";
    }

    private String generateTreeFrontendApi(String className, String url, String frontendModule, String author, String tableComment) {
        String apiName = uncapitalize(className);
        return "/**\n" +
                " * " + tableComment + " API（树表）\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "import { createCrud, http } from '@/api/core/crud';\n\n" +
                "const " + apiName + " = createCrud('" + url + "', {\n" +
                "  tree: (params) => http.get('" + url + "/tree', params),\n" +
                "});\n\n" +
                "export default { " + apiName + " };\n";
    }

    private String generateFrontendIndexPage(String className, String pageName, List<ColumnInfo> columns,
                                              String frontendModule, String tableComment, String author) {
        List<ColumnInfo> displayColumns = columns.stream()
                .filter(c -> !isBaseColumn(c.name))
                .toList();

        List<ColumnInfo> searchColumns = displayColumns.stream()
                .filter(c -> "String".equals(toJavaType(c.type)))
                .limit(2)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append(" * ").append(tableComment).append("列表页\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" -->\n");
        sb.append("<template>\n");
        sb.append("  <div class=\"").append(pageName).append("-page\">\n");

        sb.append("    <t-card class=\"search-card\">\n");
        sb.append("      <t-form :data=\"searchForm\" layout=\"inline\" @submit=\"handleSearch\" @reset=\"resetSearch\">\n");
        for (ColumnInfo col : searchColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            sb.append("        <t-form-item label=\"").append(label).append("\" name=\"").append(fieldName).append("\">\n");
            sb.append("          <t-input v-model=\"searchForm.").append(fieldName)
                    .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
            sb.append("        </t-form-item>\n");
        }
        sb.append("        <t-form-item>\n");
        sb.append("          <t-button theme=\"default\" type=\"reset\">重置</t-button>\n");
        sb.append("          <t-button theme=\"primary\" type=\"submit\">查询</t-button>\n");
        sb.append("        </t-form-item>\n");
        sb.append("      </t-form>\n");
        sb.append("    </t-card>\n\n");

        sb.append("    <t-card>\n");
        sb.append("      <template #header>\n");
        sb.append("        <div class=\"flex-between\">\n");
        sb.append("          <div></div>\n");
        sb.append("          <t-button theme=\"primary\" @click=\"add\">\n");
        sb.append("            <template #icon><t-icon name=\"add\" /></template>\n");
        sb.append("            新增\n");
        sb.append("          </t-button>\n");
        sb.append("        </div>\n");
        sb.append("      </template>\n\n");

        sb.append("      <t-table\n");
        sb.append("        :loading=\"loading\"\n");
        sb.append("        :data=\"pageParams.list\"\n");
        sb.append("        :columns=\"columns\"\n");
        sb.append("        row-key=\"id\"\n");
        sb.append("      />\n\n");

        sb.append("      <div class=\"pagination-container\">\n");
        sb.append("        <t-pagination\n");
        sb.append("          :current=\"pageParams.pageNum\"\n");
        sb.append("          :page-size=\"pageParams.pageSize\"\n");
        sb.append("          :total=\"pageParams.total\"\n");
        sb.append("          :page-size-options=\"[10, 20, 50, 100]\"\n");
        sb.append("          show-jumper\n");
        sb.append("          @current-change=\"handlePageChange\"\n");
        sb.append("          @page-size-change=\"handleSizeChange\"\n");
        sb.append("        />\n");
        sb.append("      </div>\n");
        sb.append("    </t-card>\n\n");

        sb.append("    <save-dialog\n");
        sb.append("      v-if=\"dialog.save\"\n");
        sb.append("      ref=\"saveRef\"\n");
        sb.append("      @success=\"handleSaveSuccess\"\n");
        sb.append("      @closed=\"dialog.save = false\"\n");
        sb.append("    />\n");
        sb.append("  </div>\n");
        sb.append("</template>\n\n");

        sb.append("<script setup>\n");
        sb.append("import { h, onMounted } from 'vue';\n");
        sb.append("import { DialogPlugin } from 'tdesign-vue-next';\n");
        sb.append("import { useTable } from '@/hooks/useTable';\n");
        sb.append("import saveDialog from './save.vue';\n");
        sb.append("import ").append(frontendModule).append("Api from '@/api/").append(frontendModule).append("';\n\n");

        sb.append("const columns = [\n");
        for (ColumnInfo col : displayColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            sb.append("  { colKey: '").append(fieldName).append("', title: '").append(label).append("'");
            if ("String".equals(toJavaType(col.type))) {
                sb.append(", ellipsis: true");
            }
            sb.append(" },\n");
        }
        sb.append("  {\n");
        sb.append("    colKey: 'op',\n");
        sb.append("    title: '操作',\n");
        sb.append("    width: 160,\n");
        sb.append("    align: 'center',\n");
        sb.append("    fixed: 'right',\n");
        sb.append("    cell: (h, { row }) => {\n");
        sb.append("      return h('div', { style: 'display:flex;gap:8px;justify-content:center;' }, [\n");
        sb.append("        h('a', { onClick: () => edit(row) }, '编辑'),\n");
        sb.append("        h(\n");
        sb.append("          'a',\n");
        sb.append("          {\n");
        sb.append("            style: 'color:var(--td-error-color)',\n");
        sb.append("            onClick: () => {\n");
        sb.append("              const confirmDia = DialogPlugin.confirm({\n");
        sb.append("                header: '提示',\n");
        sb.append("                body: '确定删除吗？',\n");
        sb.append("                onConfirm: () => {\n");
        sb.append("                  confirmDia.destroy();\n");
        sb.append("                  handleDelete(row);\n");
        sb.append("                },\n");
        sb.append("              });\n");
        sb.append("            },\n");
        sb.append("          },\n");
        sb.append("          '删除',\n");
        sb.append("        ),\n");
        sb.append("      ]);\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("];\n\n");

        sb.append("const {\n");
        sb.append("  loading,\n");
        sb.append("  dialog,\n");
        sb.append("  saveRef,\n");
        sb.append("  searchForm,\n");
        sb.append("  pageParams,\n");
        sb.append("  fetchData,\n");
        sb.append("  handleSearch,\n");
        sb.append("  resetSearch,\n");
        sb.append("  handleDelete,\n");
        sb.append("  handleSaveSuccess,\n");
        sb.append("  add,\n");
        sb.append("  edit,\n");
        sb.append("  handlePageChange,\n");
        sb.append("  handleSizeChange,\n");
        sb.append("} = useTable(").append(frontendModule).append("Api.").append(pageName).append(", {\n");
        sb.append("  defaultSearch: {\n");
        for (ColumnInfo col : searchColumns) {
            String fieldName = toCamelCase(col.name, false);
            sb.append("    ").append(fieldName).append(": '',\n");
        }
        sb.append("  },\n");
        sb.append("});\n");
        sb.append("onMounted(() => {\n");
        sb.append("  fetchData()\n");
        sb.append("})\n");
        sb.append("</script>\n\n");

        sb.append("<style scoped lang=\"less\">\n");
        sb.append(".").append(pageName).append("-page {\n");
        sb.append("  .search-card {\n");
        sb.append("    margin-bottom: 16px;\n");
        sb.append("  }\n\n");
        sb.append("  .flex-between {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("  }\n\n");
        sb.append("  .pagination-container {\n");
        sb.append("    margin-top: 16px;\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: flex-end;\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</style>\n");

        return sb.toString();
    }

    private String generateFrontendSavePage(String className, String pageName, List<ColumnInfo> columns,
                                             String frontendModule, String tableComment, String author) {
        List<ColumnInfo> formColumns = columns.stream()
                .filter(c -> !isBaseColumn(c.name))
                .toList();

        boolean useTwoColumn = formColumns.size() >= 6;
        String dialogWidth = useTwoColumn ? "750" : "500";

        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append(" * ").append(tableComment).append("表单弹窗\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" -->\n");
        sb.append("<template>\n");
        sb.append("  <t-dialog\n");
        sb.append("    :header=\"titleMap[mode]\"\n");
        sb.append("    v-model:visible=\"visible\"\n");
        sb.append("    :width=\"").append(dialogWidth).append("\"\n");
        sb.append("    destroy-on-close\n");
        sb.append("    @closed=\"emit('closed')\"\n");
        sb.append("  >\n");
        sb.append("    <t-form\n");
        sb.append("      :data=\"form\"\n");
        sb.append("      :rules=\"rules\"\n");
        sb.append("      ref=\"dialogForm\"\n");
        sb.append("      label-width=\"80px\"\n");
        sb.append("      :disabled=\"mode === 'show'\"\n");
        sb.append("      @submit=\"onSubmit\"\n");
        sb.append("    >\n");

        if (useTwoColumn) {
            sb.append("      <t-row :gutter=\"[16, 16]\">\n");
        }

        for (ColumnInfo col : formColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            String javaType = toJavaType(col.type);
            Map<Integer, String> statusOptions = parseStatusOptions(col.comment);

            if (statusOptions != null) {
                label = extractShortComment(col.comment);
            }

            if (useTwoColumn) {
                sb.append("        <t-col :span=\"6\">\n");
            }

            String indent = useTwoColumn ? "          " : "      ";
            sb.append(indent).append("<t-form-item label=\"").append(label).append("\" name=\"").append(fieldName).append("\">\n");

            if (statusOptions != null) {
                if (statusOptions.size() == 2 && statusOptions.containsKey(0) && statusOptions.containsKey(1)) {
                    sb.append(indent).append("  <t-switch v-model=\"form.").append(fieldName)
                            .append("\" :custom-value=\"[0, 1]\"")
                            .append(" :label=\"['").append(statusOptions.get(0)).append("', '")
                            .append(statusOptions.get(1)).append("']\" />\n");
                } else {
                    sb.append(indent).append("  <t-radio-group v-model=\"form.").append(fieldName).append("\">\n");
                    for (Map.Entry<Integer, String> entry : statusOptions.entrySet()) {
                        sb.append(indent).append("    <t-radio :value=\"").append(entry.getKey())
                                .append("\">").append(entry.getValue()).append("</t-radio>\n");
                    }
                    sb.append(indent).append("  </t-radio-group>\n");
                }
            } else if ("String".equals(javaType)) {
                if (isTextType(col.type)) {
                    sb.append(indent).append("  <t-textarea v-model=\"form.").append(fieldName)
                            .append("\" placeholder=\"请输入").append(label).append("\" />\n");
                } else {
                    sb.append(indent).append("  <t-input v-model=\"form.").append(fieldName)
                            .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
                }
            } else if ("Integer".equals(javaType) || "Long".equals(javaType)) {
                sb.append(indent).append("  <t-input-number v-model=\"form.").append(fieldName)
                        .append("\" style=\"width: 100%\" />\n");
            } else if ("java.math.BigDecimal".equals(javaType)) {
                sb.append(indent).append("  <t-input-number v-model=\"form.").append(fieldName)
                        .append("\" :decimal-places=\"2\" style=\"width: 100%\" />\n");
            } else if (javaType.startsWith("java.time.")) {
                sb.append(indent).append("  <t-date-picker v-model=\"form.").append(fieldName)
                        .append("\" enable-time-picker style=\"width: 100%\" />\n");
            } else {
                sb.append(indent).append("  <t-input v-model=\"form.").append(fieldName)
                        .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
            }
            sb.append(indent).append("</t-form-item>\n");

            if (useTwoColumn) {
                sb.append("        </t-col>\n");
            }
        }

        if (useTwoColumn) {
            sb.append("      </t-row>\n");
        }

        sb.append("    </t-form>\n");
        sb.append("    <template #footer>\n");
        sb.append("      <t-button variant=\"outline\" @click=\"visible = false\">取 消</t-button>\n");
        sb.append("      <t-button v-if=\"mode !== 'show'\" theme=\"primary\" :loading=\"isSaving\" @click=\"onSubmit\">保 存</t-button>\n");
        sb.append("    </template>\n");
        sb.append("  </t-dialog>\n");
        sb.append("</template>\n\n");

        sb.append("<script setup>\n");
        sb.append("import { MessagePlugin } from 'tdesign-vue-next';\n");
        sb.append("import { useForm } from '@/hooks/useForm';\n");
        sb.append("import ").append(frontendModule).append("Api from '@/api/").append(frontendModule).append("';\n\n");

        sb.append("const emit = defineEmits(['success', 'closed']);\n\n");

        sb.append("const {\n");
        sb.append("  visible,\n");
        sb.append("  mode,\n");
        sb.append("  isSaving,\n");
        sb.append("  formRef: dialogForm,\n");
        sb.append("  form,\n");
        sb.append("  rules,\n");
        sb.append("  titleMap,\n");
        sb.append("  open,\n");
        sb.append("  setData,\n");
        sb.append("} = useForm({\n");
        sb.append("  defaultForm: {\n");
        sb.append("    id: '',\n");
        for (ColumnInfo col : formColumns) {
            String fieldName = toCamelCase(col.name, false);
            String javaType = toJavaType(col.type);
            Map<Integer, String> statusOptions = parseStatusOptions(col.comment);
            if (statusOptions != null) {
                if (statusOptions.containsKey(1)) {
                    sb.append("    ").append(fieldName).append(": 1,\n");
                } else {
                    sb.append("    ").append(fieldName).append(": ").append(statusOptions.keySet().iterator().next()).append(",\n");
                }
            } else if ("String".equals(javaType)) {
                sb.append("    ").append(fieldName).append(": '',\n");
            } else if ("Integer".equals(javaType) || "Long".equals(javaType) || "java.math.BigDecimal".equals(javaType)) {
                sb.append("    ").append(fieldName).append(": null,\n");
            } else if (javaType.startsWith("java.time.")) {
                sb.append("    ").append(fieldName).append(": '',\n");
            } else {
                sb.append("    ").append(fieldName).append(": '',\n");
            }
        }
        sb.append("  },\n");
        sb.append("  rules: {\n");
        // Add required rule for first string field
        for (ColumnInfo col : formColumns) {
            if ("String".equals(toJavaType(col.type))) {
                String label = col.comment.isEmpty() ? col.name : col.comment;
                String fieldName = toCamelCase(col.name, false);
                sb.append("    ").append(fieldName).append(": [{ required: true, message: '请输入")
                        .append(label).append("', trigger: 'blur' }],\n");
                break;
            }
        }
        sb.append("  },\n");
        sb.append("});\n\n");

        sb.append("const onSubmit = async () => {\n");
        sb.append("  let validateResult;\n");
        sb.append("  try {\n");
        sb.append("    validateResult = await dialogForm.value?.validate();\n");
        sb.append("  } catch {\n");
        sb.append("    validateResult = false;\n");
        sb.append("  }\n");
        sb.append("  if (validateResult !== true) return;\n\n");
        sb.append("  isSaving.value = true;\n");
        sb.append("  try {\n");
        sb.append("    const res =\n");
        sb.append("      mode.value === 'add'\n");
        sb.append("        ? await ").append(frontendModule).append("Api.").append(pageName).append(".create(form)\n");
        sb.append("        : await ").append(frontendModule).append("Api.").append(pageName).append(".update(form);\n");
        sb.append("    if (res.code === 200) {\n");
        sb.append("      MessagePlugin.success(mode.value === 'add' ? '新增成功' : '修改成功');\n");
        sb.append("      visible.value = false;\n");
        sb.append("      emit('success', form, mode.value);\n");
        sb.append("    }\n");
        sb.append("  } finally {\n");
        sb.append("    isSaving.value = false;\n");
        sb.append("  }\n");
        sb.append("};\n\n");

        sb.append("defineExpose({ open, setData });\n");
        sb.append("</script>\n");

        return sb.toString();
    }

    private String generateFrontendRouter(String className, String pageName, String frontendModule, String tableComment) {
        return "// 在 src/router/modules/" + frontendModule + ".ts 中添加以下路由配置\n" +
                "{\n" +
                "  path: '" + pageName + "',\n" +
                "  name: '" + className + "',\n" +
                "  component: () => import('@/pages/" + frontendModule + "/" + pageName + "/index.vue'),\n" +
                "  meta: {\n" +
                "    title: {\n" +
                "      zh_CN: '" + tableComment + "',\n" +
                "      en_US: '" + className + "',\n" +
                "    },\n" +
                "  },\n" +
                "},\n";
    }

    private String generateDtoRequest(String className, List<ColumnInfo> columns,
                                       String basePkg, String packageName, String author, String tableComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".").append(packageName).append(".domain.dto;\n\n");
        sb.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        sb.append("import lombok.Data;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(tableComment).append("请求DTO\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" */\n");
        sb.append("@Data\n");
        sb.append("@Schema(description = \"").append(className).append("Request\")\n");
        sb.append("public class ").append(className).append("Request {\n\n");

        // id 必须保留：update 端点的 @DistributedLock(key = "'x:' + #req.id") 与
        // service.updateById(entity) 都依赖主键；缺失会导致 SpEL 求值报错（update 直接 500）。
        // 与同源参考 DemoExampleRequest 保持一致；其余 base 列（审计 / 租户 / 部门）不对外暴露。
        sb.append("    @Schema(description = \"ID（修改时必填）\")\n");
        sb.append("    private Long id;\n\n");

        for (ColumnInfo col : columns) {
            if (isBaseColumn(col.name)) continue;
            String javaType = toJavaType(col.type);
            String fieldName = toCamelCase(col.name, false);
            sb.append("    @Schema(description = \"")
                    .append(escapeJavaLiteral(col.comment.isEmpty() ? col.name : col.comment)).append("\")\n");
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateDtoQueryRequest(String className, List<ColumnInfo> columns,
                                            String basePkg, String packageName, String author, String tableComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".").append(packageName).append(".domain.dto;\n\n");
        sb.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        sb.append("import lombok.Data;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(tableComment).append("查询请求DTO\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" */\n");
        sb.append("@Data\n");
        sb.append("@Schema(description = \"").append(className).append("QueryRequest\")\n");
        sb.append("public class ").append(className).append("QueryRequest {\n\n");

        for (ColumnInfo col : columns) {
            if (isBaseColumn(col.name)) continue;
            if (!"String".equals(toJavaType(col.type))) continue;
            String fieldName = toCamelCase(col.name, false);
            sb.append("    @Schema(description = \"")
                    .append(escapeJavaLiteral(col.comment.isEmpty() ? col.name : col.comment)).append("\")\n");
            sb.append("    private String ").append(fieldName).append(";\n\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String generateMenuSql(String tableName, String pageName, String frontendModule, String tableComment) {
        long menuId = IdUtils.snowflakeShort();
        long parentId = 0;
        int sort = 99;

        try (Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps1 = conn.prepareStatement(
                     "SELECT id FROM sys_menu WHERE path = ? AND type = 'M' LIMIT 1");
             java.sql.PreparedStatement ps2 = conn.prepareStatement(
                     "SELECT COALESCE(MAX(sort), 0) + 1 FROM sys_menu WHERE parent_id = ?")) {

            ps1.setString(1, frontendModule);
            try (ResultSet rs = ps1.executeQuery()) {
                if (rs.next()) {
                    parentId = rs.getLong("id");
                }
            }

            ps2.setLong(1, parentId);
            try (ResultSet rs = ps2.executeQuery()) {
                if (rs.next()) {
                    sort = rs.getInt(1);
                }
            }
        } catch (Exception e) {
            // fallback to defaults
        }

        String perm = frontendModule + ":" + pageName + ":";
        // 与 Controller 的 @SaCheckPermission 权限码同源：菜单(C) + 4 个按钮(F)。
        // 不生成按钮权限会导致「后端要权限码、菜单却没配」→ 新模块一登录即无权访问。
        // path 唯一键：菜单用 pageName，按钮用 NULL（唯一索引允许多个 NULL）。
        return "-- " + escapeSqlLiteral(tableComment) + " 菜单与按钮权限（权限码与 Controller @SaCheckPermission 一致）\n" +
                "INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES\n" +
                "(" + menuId + ", " + parentId + ", '" + escapeSqlLiteral(tableComment) + "', " + sort + ", '"
                + frontendModule + "/" + pageName + "/index', '" + pageName + "', 'C', 1, '" + perm + "view', 0, 'view-module', '" + escapeSqlLiteral(tableComment) + "菜单');\n" +
                "INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES\n" +
                "(" + (menuId + 1) + ", " + menuId + ", '" + escapeSqlLiteral(tableComment) + "查询', 1, '', NULL, 'F', 1, '" + perm + "view', 0, '#', ''),\n" +
                "(" + (menuId + 2) + ", " + menuId + ", '" + escapeSqlLiteral(tableComment) + "新增', 2, '', NULL, 'F', 1, '" + perm + "add', 0, '#', ''),\n" +
                "(" + (menuId + 3) + ", " + menuId + ", '" + escapeSqlLiteral(tableComment) + "修改', 3, '', NULL, 'F', 1, '" + perm + "update', 0, '#', ''),\n" +
                "(" + (menuId + 4) + ", " + menuId + ", '" + escapeSqlLiteral(tableComment) + "删除', 4, '', NULL, 'F', 1, '" + perm + "delete', 0, '#', '');";
    }

    private String generateTreeEntity(String tableName, String className, List<ColumnInfo> columns,
                                       String basePkg, String packageName, String author, String tableComment,
                                       String parentField, String nameField) {
        Set<String> columnNames = new java.util.HashSet<>();
        for (ColumnInfo col : columns) {
            columnNames.add(col.name);
        }

        boolean missingTenantId = !columnNames.contains("tenant_id");
        boolean missingDeptId = !columnNames.contains("dept_id");
        boolean missingCreateBy = !columnNames.contains("create_by");
        boolean missingCreateTime = !columnNames.contains("create_time");
        boolean missingUpdateBy = !columnNames.contains("update_by");
        boolean missingUpdateTime = !columnNames.contains("update_time");

        boolean hasIgnoreAnnotation = missingTenantId || missingDeptId || missingCreateBy
                || missingCreateTime || missingUpdateBy || missingUpdateTime;

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(basePkg).append(".").append(packageName).append(".entity;\n\n");
        sb.append("import com.baomidou.mybatisplus.annotation.TableName;\n");
        sb.append("import ").append(basePkg).append(".common.data.base.BaseEntity;\n");
        if (hasIgnoreAnnotation) {
            sb.append("import ").append(basePkg).append(".common.core.annotation.IgnoreBaseField;\n");
        }
        sb.append("import io.swagger.v3.oas.annotations.media.Schema;\n");
        sb.append("import lombok.Data;\n");
        sb.append("import lombok.EqualsAndHashCode;\n");
        sb.append("import java.util.List;\n\n");
        sb.append("/**\n");
        sb.append(" * ").append(tableComment).append("实体类（树表）\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" */\n");
        sb.append("@Data\n");
        sb.append("@EqualsAndHashCode(callSuper = true)\n");
        sb.append("@TableName(\"").append(tableName).append("\")\n");
        sb.append("@Schema(description = \"").append(className).append("\")\n");
        if (hasIgnoreAnnotation) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            if (missingTenantId) parts.add("tenantId = true");
            if (missingDeptId) parts.add("deptId = true");
            if (missingCreateBy) parts.add("createBy = true");
            if (missingCreateTime) parts.add("createTime = true");
            if (missingUpdateBy) parts.add("updateBy = true");
            if (missingUpdateTime) parts.add("updateTime = true");
            sb.append("@IgnoreBaseField(").append(String.join(", ", parts)).append(")\n");
        }
        sb.append("public class ").append(className).append(" extends BaseEntity {\n\n");

        for (ColumnInfo col : columns) {
            if (isBaseColumn(col.name)) continue;
            String javaType = toJavaType(col.type);
            String fieldName = toCamelCase(col.name, false);
            sb.append("    @Schema(description = \"")
                    .append(escapeJavaLiteral(col.comment.isEmpty() ? col.name : col.comment)).append("\")\n");
            sb.append("    private ").append(javaType).append(" ").append(fieldName).append(";\n\n");
        }

        String parentFieldName = toCamelCase(parentField, false);
        String nameFieldName = toCamelCase(nameField, false);
        sb.append("    @Schema(description = \"子节点列表\")\n");
        sb.append("    @com.baomidou.mybatisplus.annotation.TableField(exist = false)\n");
        sb.append("    private List<").append(className).append("> children;\n\n");

        sb.append("    @Schema(description = \"父节点ID\")\n");
        sb.append("    @com.baomidou.mybatisplus.annotation.TableField(exist = false)\n");
        sb.append("    private Long ").append(parentFieldName).append("Value;\n\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String generateTreeService(String className, String basePkg, String packageName,
                                        String author, String tableComment, String parentField, String nameField) {
        String parentFieldName = toCamelCase(parentField, false);
        String nameFieldName = toCamelCase(nameField, false);
        return "/**\n" +
                " * " + tableComment + "服务类（树表）\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".service;\n\n" +
                "import cn.hutool.core.util.StrUtil;\n" +
                "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n" +
                "import " + basePkg + ".common.data.base.BaseService;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".mapper." + className + "Mapper;\n" +
                "import org.springframework.stereotype.Service;\n\n" +
                "import java.util.ArrayList;\n" +
                "import java.util.List;\n" +
                "import java.util.Map;\n" +
                "import java.util.stream.Collectors;\n\n" +
                "@Service\n" +
                "public class " + className + "Service extends BaseService<" + className + "Mapper, " + className + "> {\n\n" +
                "    public List<" + className + "> tree() {\n" +
                "        return tree(null);\n" +
                "    }\n\n" +
                "    public List<" + className + "> tree(" + className + " param) {\n" +
                "        LambdaQueryWrapper<" + className + "> queryWrapper = new LambdaQueryWrapper<>();\n" +
                "        if (param != null) {\n" +
                "            if (StrUtil.isNotBlank(param.get" + capitalize(nameFieldName) + "())) {\n" +
                "                queryWrapper.like(" + className + "::get" + capitalize(nameFieldName) + ", param.get" + capitalize(nameFieldName) + "());\n" +
                "            }\n" +
                "        }\n" +
                "        List<" + className + "> allList = baseMapper.selectList(queryWrapper);\n" +
                "        return buildTree(allList);\n" +
                "    }\n\n" +
                "    public boolean hasChildren(Long parentId) {\n" +
                "        LambdaQueryWrapper<" + className + "> queryWrapper = new LambdaQueryWrapper<>();\n" +
                "        queryWrapper.eq(" + className + "::get" + capitalize(parentFieldName) + ", parentId);\n" +
                "        return baseMapper.selectCount(queryWrapper) > 0;\n" +
                "    }\n\n" +
                "    private List<" + className + "> buildTree(List<" + className + "> list) {\n" +
                "        Map<Long, List<" + className + ">> grouped = list.stream()\n" +
                "                .collect(Collectors.groupingBy(item -> {\n" +
                "                    Long pid = item.get" + capitalize(parentFieldName) + "();\n" +
                "                    return pid != null ? pid : 0L;\n" +
                "                }));\n" +
                "        List<" + className + "> roots = grouped.getOrDefault(0L, new ArrayList<>());\n" +
                "        for (" + className + " root : roots) {\n" +
                "            root.setChildren(findChildren(root.getId(), grouped));\n" +
                "        }\n" +
                "        return roots;\n" +
                "    }\n\n" +
                "    private List<" + className + "> findChildren(Long parentId, Map<Long, List<" + className + ">> grouped) {\n" +
                "        List<" + className + "> children = grouped.getOrDefault(parentId, new ArrayList<>());\n" +
                "        for (" + className + " child : children) {\n" +
                "            child.setChildren(findChildren(child.getId(), grouped));\n" +
                "        }\n" +
                "        return children;\n" +
                "    }\n" +
                "}\n";
    }

    private String generateTreeController(String className, String tableName, String basePkg,
                                           String packageName, String author, String tableComment, String parentField) {
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);
        String url = "/" + frontendModule + "/" + pageName;
        String parentFieldName = toCamelCase(parentField, false);
        String perm = frontendModule + ":" + pageName + ":";

        return "/**\n" +
                " * " + tableComment + "控制器（树表）\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".controller;\n\n" +
                "import " + basePkg + ".log.annotation.SLog;\n" +
                "import " + basePkg + ".security.annotation.RateLimit;\n" +
                "import " + basePkg + ".security.annotation.DistributedLock;\n" +
                "import " + basePkg + ".common.core.base.BaseConstant;\n" +
                "import " + basePkg + ".common.data.base.BaseController;\n" +
                "import " + basePkg + ".common.web.domain.dto.R;\n" +
                "import cn.dev33.satoken.annotation.SaCheckPermission;\n" +
                "import cn.hutool.core.bean.BeanUtil;\n" +
                "import jakarta.validation.Valid;\n" +
                "import " + basePkg + "." + packageName + ".domain.dto." + className + "Request;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".service." + className + "Service;\n" +
                "import io.swagger.v3.oas.annotations.Operation;\n" +
                "import io.swagger.v3.oas.annotations.tags.Tag;\n" +
                "import org.springframework.web.bind.annotation.*;\n\n" +
                "import java.util.List;\n" +
                "import java.util.Map;\n\n" +
                "@Tag(name = \"" + className + "\")\n" +
                "@RestController\n" +
                "@RequestMapping(\"" + url + "\")\n" +
                "public class " + className + "Controller extends BaseController<" + className + "Service, " + className + "> {\n\n" +
                "    @GetMapping(\"/tree\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"获取树形结构\")\n" +
                "    public R<List<" + className + ">> tree(@RequestParam Map<String, Object> map) {\n" +
                "        " + className + " param = null;\n" +
                "        if (map != null && !map.isEmpty()) {\n" +
                "            param = new " + className + "();\n" +
                "            if (map.containsKey(\"" + parentFieldName + "\")) {\n" +
                "                param.set" + capitalize(parentFieldName) + "(Long.valueOf(map.get(\"" + parentFieldName + "\").toString()));\n" +
                "            }\n" +
                "        }\n" +
                "        return R.success(service.tree(param));\n" +
                "    }\n\n" +
                "    @GetMapping(\"/detail/{id}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"根据ID获取详情\")\n" +
                "    public R<" + className + "> detail(@PathVariable(\"id\") Long id) {\n" +
                "        return R.success(service.getById(id));\n" +
                "    }\n\n" +
                "    @PostMapping(\"/create\")\n" +
                "    @Operation(summary = \"新增\")\n" +
                "    @SLog(title = \"新增" + className + "\")\n" +
                "    @RateLimit(value = \"default\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "add\")\n" +
                "    public R<String> create(@RequestBody @Valid " + className + "Request req) {\n" +
                "        " + className + " entity = BeanUtil.copyProperties(req, " + className + ".class);\n" +
                "        service.save(entity);\n" +
                "        return R.successMsg(\"新增成功\");\n" +
                "    }\n\n" +
                "    @PutMapping(\"/update\")\n" +
                "    @Operation(summary = \"修改\")\n" +
                "    @SLog(title = \"修改" + className + "\")\n" +
                "    @DistributedLock(key = \"'" + perm + "' + #req.id\", expireTime = 10000)\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "update\")\n" +
                "    public R<String> update(@RequestBody @Valid " + className + "Request req) {\n" +
                "        " + className + " entity = BeanUtil.copyProperties(req, " + className + ".class);\n" +
                "        service.updateById(entity);\n" +
                "        return R.successMsg(\"修改成功\");\n" +
                "    }\n\n" +
                "    @DeleteMapping(\"/{id}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "delete\")\n" +
                "    @Operation(summary = \"删除\")\n" +
                "    @SLog(title = \"删除" + className + "\")\n" +
                "    public R<String> delete(@PathVariable(\"id\") Long id) {\n" +
                "        if (service.hasChildren(id)) {\n" +
                "            return R.error(\"存在子节点，不允许删除\");\n" +
                "        }\n" +
                "        service.removeById(id);\n" +
                "        return R.successMsg(\"删除成功\");\n" +
                "    }\n" +
                "}\n";
    }

    private String generateTreeFrontendIndexPage(String className, String pageName, List<ColumnInfo> columns,
                                                  String frontendModule, String tableComment, String author,
                                                  String parentField, String nameField) {
        String parentFieldName = toCamelCase(parentField, false);
        String nameFieldName = toCamelCase(nameField, false);

        List<ColumnInfo> displayColumns = columns.stream()
                .filter(c -> !isBaseColumn(c.name))
                .toList();

        List<ColumnInfo> searchColumns = displayColumns.stream()
                .filter(c -> "String".equals(toJavaType(c.type)))
                .limit(2)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append(" * ").append(tableComment).append("树表页面\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" -->\n");
        sb.append("<template>\n");
        sb.append("  <div class=\"").append(pageName).append("-page\">\n");

        // 搜索栏
        if (!searchColumns.isEmpty()) {
            sb.append("    <t-card class=\"search-card\">\n");
            sb.append("      <t-form :data=\"searchForm\" layout=\"inline\" @submit=\"handleSearch\" @reset=\"resetSearch\">\n");
            for (ColumnInfo col : searchColumns) {
                String label = col.comment.isEmpty() ? col.name : col.comment;
                String fieldName = toCamelCase(col.name, false);
                sb.append("        <t-form-item label=\"").append(label).append("\" name=\"").append(fieldName).append("\">\n");
                sb.append("          <t-input v-model=\"searchForm.").append(fieldName)
                        .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
                sb.append("        </t-form-item>\n");
            }
            sb.append("        <t-form-item>\n");
            sb.append("          <t-button theme=\"default\" type=\"reset\">重置</t-button>\n");
            sb.append("          <t-button theme=\"primary\" type=\"submit\">查询</t-button>\n");
            sb.append("          <t-button theme=\"success\" @click=\"add\">新增</t-button>\n");
            sb.append("        </t-form-item>\n");
            sb.append("      </t-form>\n");
            sb.append("    </t-card>\n\n");
        }

        // 树形表格
        sb.append("    <t-card>\n");
        if (searchColumns.isEmpty()) {
            sb.append("      <template #header>\n");
            sb.append("        <div class=\"flex-between\">\n");
            sb.append("          <div></div>\n");
            sb.append("          <t-button theme=\"primary\" @click=\"add\">\n");
            sb.append("            <template #icon><t-icon name=\"add\" /></template>\n");
            sb.append("            新增\n");
            sb.append("          </t-button>\n");
            sb.append("        </div>\n");
            sb.append("      </template>\n");
        }
        sb.append("      <t-enhanced-table\n");
        sb.append("        :loading=\"loading\"\n");
        sb.append("        :data=\"treeData\"\n");
        sb.append("        :columns=\"columns\"\n");
        sb.append("        row-key=\"id\"\n");
        sb.append("        :tree=\"{ childrenKey: 'children', treeNodeColumnIndex: 0 }\"\n");
        sb.append("        stripe\n");
        sb.append("        bordered\n");
        sb.append("      />\n");
        sb.append("    </t-card>\n\n");

        // 编辑弹窗
        sb.append("    <save-dialog\n");
        sb.append("      v-if=\"dialog.save\"\n");
        sb.append("      ref=\"saveRef\"\n");
        sb.append("      @success=\"handleSaveSuccess\"\n");
        sb.append("      @closed=\"dialog.save = false\"\n");
        sb.append("    />\n");
        sb.append("  </div>\n");
        sb.append("</template>\n\n");

        // Script
        sb.append("<script setup>\n");
        sb.append("import { h, ref, onMounted } from 'vue';\n");
        sb.append("import { MessagePlugin, DialogPlugin } from 'tdesign-vue-next';\n");
        sb.append("import ").append(frontendModule).append("Api from '@/api/").append(frontendModule).append("';\n");
        sb.append("import saveDialog from './save.vue';\n\n");

        // 列定义
        sb.append("const dialog = ref({ save: false });\n");
        sb.append("const saveRef = ref(null);\n");
        sb.append("const loading = ref(false);\n");
        sb.append("const treeData = ref([]);\n");
        if (!searchColumns.isEmpty()) {
            sb.append("const searchForm = ref({\n");
            for (ColumnInfo col : searchColumns) {
                String fieldName = toCamelCase(col.name, false);
                sb.append("  ").append(fieldName).append(": '',\n");
            }
            sb.append("});\n");
        }
        sb.append("\n");

        sb.append("const columns = [\n");
        for (ColumnInfo col : displayColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            sb.append("  { colKey: '").append(fieldName).append("', title: '").append(label).append("'");
            if (fieldName.equals(nameFieldName)) {
                sb.append(", width: 200");
            } else if ("String".equals(toJavaType(col.type))) {
                sb.append(", ellipsis: true");
            }
            sb.append(" },\n");
        }
        // 操作列
        sb.append("  {\n");
        sb.append("    colKey: 'op',\n");
        sb.append("    title: '操作',\n");
        sb.append("    width: 200,\n");
        sb.append("    align: 'center',\n");
        sb.append("    fixed: 'right',\n");
        sb.append("    cell: (h, { row }) => {\n");
        sb.append("      const btns = [];\n");
        sb.append("      btns.push(h('a', { onClick: () => addChild(row) }, '新增子节点'));\n");
        sb.append("      btns.push(h('a', { onClick: () => edit(row) }, '编辑'));\n");
        sb.append("      btns.push(\n");
        sb.append("        h(\n");
        sb.append("          'a',\n");
        sb.append("          {\n");
        sb.append("            style: 'color:var(--td-error-color)',\n");
        sb.append("            onClick: () => {\n");
        sb.append("              const confirmDia = DialogPlugin.confirm({\n");
        sb.append("                header: '提示',\n");
        sb.append("                body: '确定删除吗？',\n");
        sb.append("                onConfirm: () => {\n");
        sb.append("                  confirmDia.destroy();\n");
        sb.append("                  handleDelete(row);\n");
        sb.append("                },\n");
        sb.append("              });\n");
        sb.append("            },\n");
        sb.append("          },\n");
        sb.append("          '删除',\n");
        sb.append("        ),\n");
        sb.append("      );\n");
        sb.append("      return h('div', { style: 'display:flex;gap:8px;justify-content:center;' }, btns);\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("];\n\n");

        // 数据加载
        sb.append("const fetchData = async () => {\n");
        sb.append("  loading.value = true;\n");
        sb.append("  try {\n");
        if (!searchColumns.isEmpty()) {
            sb.append("    const res = await ").append(frontendModule).append("Api.").append(pageName).append(".tree(searchForm.value);\n");
        } else {
            sb.append("    const res = await ").append(frontendModule).append("Api.").append(pageName).append(".tree();\n");
        }
        sb.append("    treeData.value = res?.data || [];\n");
        sb.append("  } finally {\n");
        sb.append("    loading.value = false;\n");
        sb.append("  }\n");
        sb.append("};\n\n");

        if (!searchColumns.isEmpty()) {
            sb.append("const handleSearch = () => {\n");
            sb.append("  fetchData();\n");
            sb.append("};\n\n");
            sb.append("const resetSearch = () => {\n");
            sb.append("  searchForm.value = {\n");
            for (ColumnInfo col : searchColumns) {
                String fieldName = toCamelCase(col.name, false);
                sb.append("    ").append(fieldName).append(": '',\n");
            }
            sb.append("  };\n");
            sb.append("  fetchData();\n");
            sb.append("};\n\n");
        }

        // 操作方法
        sb.append("const add = () => {\n");
        sb.append("  dialog.value.save = true;\n");
        sb.append("  setTimeout(() => {\n");
        sb.append("    saveRef.value?.open('add');\n");
        sb.append("  }, 0);\n");
        sb.append("};\n\n");

        sb.append("const addChild = (row) => {\n");
        sb.append("  dialog.value.save = true;\n");
        sb.append("  setTimeout(() => {\n");
        sb.append("    saveRef.value?.open('add');\n");
        sb.append("    saveRef.value?.set").append(capitalize(parentFieldName)).append("(row.id);\n");
        sb.append("  }, 0);\n");
        sb.append("};\n\n");

        sb.append("const edit = (row) => {\n");
        sb.append("  dialog.value.save = true;\n");
        sb.append("  setTimeout(() => {\n");
        sb.append("    saveRef.value?.open('edit');\n");
        sb.append("    saveRef.value?.setData(row);\n");
        sb.append("  }, 0);\n");
        sb.append("};\n\n");

        sb.append("const handleDelete = async (row) => {\n");
        sb.append("  try {\n");
        sb.append("    const res = await ").append(frontendModule).append("Api.").append(pageName).append(".delete(row.id);\n");
        sb.append("    if (res.code === 200) {\n");
        sb.append("      MessagePlugin.success('删除成功');\n");
        sb.append("      fetchData();\n");
        sb.append("    } else {\n");
        sb.append("      MessagePlugin.error(res.message || '删除失败');\n");
        sb.append("    }\n");
        sb.append("  } catch (e) {\n");
        sb.append("    MessagePlugin.error(e.message || '删除失败');\n");
        sb.append("  }\n");
        sb.append("};\n\n");

        sb.append("const handleSaveSuccess = () => {\n");
        sb.append("  dialog.value.save = false;\n");
        sb.append("  fetchData();\n");
        sb.append("};\n\n");

        sb.append("onMounted(() => {\n");
        sb.append("  fetchData();\n");
        sb.append("});\n");
        sb.append("</script>\n\n");

        sb.append("<style scoped lang=\"less\">\n");
        sb.append(".").append(pageName).append("-page {\n");
        sb.append("  .search-card {\n");
        sb.append("    margin-bottom: 16px;\n");
        sb.append("  }\n\n");
        sb.append("  .flex-between {\n");
        sb.append("    display: flex;\n");
        sb.append("    justify-content: space-between;\n");
        sb.append("    align-items: center;\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("</style>\n");
        return sb.toString();
    }

    private String generateTreeFrontendSavePage(String className, String pageName, List<ColumnInfo> columns,
                                                 String frontendModule, String tableComment, String author,
                                                 String parentField, String nameField) {
        String parentFieldName = toCamelCase(parentField, false);
        String nameFieldName = toCamelCase(nameField, false);

        List<ColumnInfo> formColumns = columns.stream()
                .filter(c -> !isBaseColumn(c.name))
                .toList();

        // 构建 defaultForm 字段
        StringBuilder defaultFormSb = new StringBuilder();
        defaultFormSb.append("    id: '',\n");
        defaultFormSb.append("    ").append(parentFieldName).append(": 0,\n");
        for (ColumnInfo col : formColumns) {
            String fieldName = toCamelCase(col.name, false);
            if (fieldName.equals(parentFieldName)) continue;
            String javaType = toJavaType(col.type);
            if ("Integer".equals(javaType) || "Long".equals(javaType)) {
                defaultFormSb.append("    ").append(fieldName).append(": null,\n");
            } else if ("java.math.BigDecimal".equals(javaType)) {
                defaultFormSb.append("    ").append(fieldName).append(": null,\n");
            } else {
                defaultFormSb.append("    ").append(fieldName).append(": '',\n");
            }
        }

        // 构建 rules
        StringBuilder rulesSb = new StringBuilder();
        for (ColumnInfo col : formColumns) {
            String fieldName = toCamelCase(col.name, false);
            String label = col.comment.isEmpty() ? col.name : col.comment;
            if (fieldName.equals(nameFieldName)) {
                rulesSb.append("    ").append(fieldName).append(": [{ required: true, message: '请输入").append(label).append("', trigger: 'blur' }],\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append(" * ").append(tableComment).append("树表编辑弹窗\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" -->\n");
        sb.append("<template>\n");
        sb.append("  <t-dialog\n");
        sb.append("    :header=\"titleMap[mode]\"\n");
        sb.append("    v-model:visible=\"visible\"\n");
        sb.append("    :width=\"500\"\n");
        sb.append("    destroy-on-close\n");
        sb.append("    @closed=\"emit('closed')\"\n");
        sb.append("  >\n");
        sb.append("    <t-form\n");
        sb.append("      :data=\"form\"\n");
        sb.append("      :rules=\"rules\"\n");
        sb.append("      ref=\"dialogForm\"\n");
        sb.append("      label-width=\"100px\"\n");
        sb.append("      :disabled=\"mode === 'show'\"\n");
        sb.append("    >\n");

        // 父节点选择（放在表单第一个）
        sb.append("      <t-form-item label=\"上级节点\" name=\"").append(parentFieldName).append("\">\n");
        sb.append("        <t-tree-select\n");
        sb.append("          v-model=\"form.").append(parentFieldName).append("\"\n");
        sb.append("          :data=\"treeOptions\"\n");
        sb.append("          :tree-props=\"{ keys: { label: '").append(nameFieldName).append("', value: 'id', children: 'children' } }\"\n");
        sb.append("          placeholder=\"请选择上级节点\"\n");
        sb.append("          clearable\n");
        sb.append("        />\n");
        sb.append("      </t-form-item>\n");

        // 其他表单字段
        for (ColumnInfo col : formColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            if (fieldName.equals(parentFieldName)) continue;

            String javaType = toJavaType(col.type);
            Map<Integer, String> statusOptions = parseStatusOptions(col.comment);

            if (statusOptions != null) {
                label = extractShortComment(col.comment);
            }

            sb.append("      <t-form-item label=\"").append(label).append("\" name=\"").append(fieldName).append("\">\n");

            if (statusOptions != null) {
                if (statusOptions.size() == 2 && statusOptions.containsKey(0) && statusOptions.containsKey(1)) {
                    sb.append("        <t-switch v-model=\"form.").append(fieldName)
                            .append("\" :custom-value=\"[0, 1]\"")
                            .append(" :label=\"['").append(statusOptions.get(0)).append("', '")
                            .append(statusOptions.get(1)).append("']\" />\n");
                } else {
                    sb.append("        <t-radio-group v-model=\"form.").append(fieldName).append("\">\n");
                    for (Map.Entry<Integer, String> entry : statusOptions.entrySet()) {
                        sb.append("          <t-radio :value=\"").append(entry.getKey())
                                .append("\">").append(entry.getValue()).append("</t-radio>\n");
                    }
                    sb.append("        </t-radio-group>\n");
                }
            } else if ("String".equals(javaType)) {
                if (isTextType(col.type)) {
                    sb.append("        <t-textarea v-model=\"form.").append(fieldName)
                            .append("\" placeholder=\"请输入").append(label).append("\" />\n");
                } else {
                    sb.append("        <t-input v-model=\"form.").append(fieldName)
                            .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
                }
            } else if ("Integer".equals(javaType) || "Long".equals(javaType)) {
                sb.append("        <t-input-number v-model=\"form.").append(fieldName)
                        .append("\" style=\"width: 100%\" />\n");
            } else if ("java.math.BigDecimal".equals(javaType)) {
                sb.append("        <t-input-number v-model=\"form.").append(fieldName)
                        .append("\" :decimal-places=\"2\" style=\"width: 100%\" />\n");
            } else if (javaType.startsWith("java.time.")) {
                sb.append("        <t-date-picker v-model=\"form.").append(fieldName)
                        .append("\" enable-time-picker style=\"width: 100%\" />\n");
            } else {
                sb.append("        <t-input v-model=\"form.").append(fieldName)
                        .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
            }
            sb.append("      </t-form-item>\n");
        }

        sb.append("    </t-form>\n");
        sb.append("    <template #footer>\n");
        sb.append("      <t-button variant=\"outline\" @click=\"visible = false\">取 消</t-button>\n");
        sb.append("      <t-button v-if=\"mode !== 'show'\" theme=\"primary\" :loading=\"isSaving\" @click=\"onSubmit\">保 存</t-button>\n");
        sb.append("    </template>\n");
        sb.append("  </t-dialog>\n");
        sb.append("</template>\n\n");

        sb.append("<script setup>\n");
        sb.append("import { ref, onMounted } from 'vue';\n");
        sb.append("import { MessagePlugin } from 'tdesign-vue-next';\n");
        sb.append("import { useForm } from '@/hooks/useForm';\n");
        sb.append("import ").append(frontendModule).append("Api from '@/api/").append(frontendModule).append("';\n\n");

        sb.append("const emit = defineEmits(['success', 'closed']);\n\n");

        sb.append("const treeOptions = ref([]);\n\n");

        sb.append("const {\n");
        sb.append("  visible,\n");
        sb.append("  mode,\n");
        sb.append("  isSaving,\n");
        sb.append("  formRef: dialogForm,\n");
        sb.append("  form,\n");
        sb.append("  rules,\n");
        sb.append("  titleMap,\n");
        sb.append("  open,\n");
        sb.append("  setData,\n");
        sb.append("  submit: formSubmit,\n");
        sb.append("} = useForm({\n");
        sb.append("  defaultForm: {\n");
        sb.append(defaultFormSb.toString());
        sb.append("  },\n");
        sb.append("  rules: {\n");
        sb.append(rulesSb.toString());
        sb.append("  },\n");
        sb.append("});\n\n");

        sb.append("const loadTree = async () => {\n");
        sb.append("  const res = await ").append(frontendModule).append("Api.").append(pageName).append(".tree();\n");
        sb.append("  const list = res?.data || [];\n");
        sb.append("  treeOptions.value = [{ id: 0, ").append(nameFieldName).append(": '根节点', children: list }];\n");
        sb.append("};\n\n");

        sb.append("const set").append(capitalize(parentFieldName)).append(" = (parentId) => {\n");
        sb.append("  form.").append(parentFieldName).append(" = parentId;\n");
        sb.append("};\n\n");

        sb.append("const onSubmit = async () => {\n");
        sb.append("  let valid;\n");
        sb.append("  try {\n");
        sb.append("    valid = await dialogForm.value?.validate();\n");
        sb.append("  } catch {\n");
        sb.append("    valid = false;\n");
        sb.append("  }\n");
        sb.append("  if (valid !== true) return;\n\n");
        sb.append("  const submitData = { ...form };\n");
        sb.append("  if (submitData.id === '' || submitData.id === undefined || submitData.id === null) {\n");
        sb.append("    delete submitData.id;\n");
        sb.append("  }\n\n");
        sb.append("  isSaving.value = true;\n");
        sb.append("  try {\n");
        sb.append("    const res = mode.value === 'add' ? await ").append(frontendModule).append("Api.").append(pageName).append(".create(submitData) : await ").append(frontendModule).append("Api.").append(pageName).append(".update(submitData);\n");
        sb.append("    if (res && res.code === 200) {\n");
        sb.append("      MessagePlugin.success(mode.value === 'add' ? '新增成功' : '修改成功');\n");
        sb.append("      visible.value = false;\n");
        sb.append("      emit('success', form, mode.value);\n");
        sb.append("    }\n");
        sb.append("  } finally {\n");
        sb.append("    isSaving.value = false;\n");
        sb.append("  }\n");
        sb.append("};\n\n");

        sb.append("onMounted(() => {\n");
        sb.append("  loadTree();\n");
        sb.append("});\n\n");

        sb.append("defineExpose({ open, setData, set").append(capitalize(parentFieldName)).append(" });\n");
        sb.append("</script>\n");
        return sb.toString();
    }

    private String generateMasterService(String className, String basePkg, String packageName,
                                          String author, String tableComment,
                                          String childTableName, String childForeignKey, String childPackageName) {
        String childClassName = toCamelCase(childTableName, true);
        String childPkg = (childPackageName != null && !childPackageName.isEmpty()) ? childPackageName : packageName;
        String childFieldKey = toCamelCase(childForeignKey, false);

        return "/**\n" +
                " * " + tableComment + "服务类（主子表）\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".service;\n\n" +
                "import cn.hutool.core.util.StrUtil;\n" +
                "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n" +
                "import " + basePkg + ".common.data.base.BaseService;\n" +
                "import " + basePkg + ".common.core.util.page.PageReq;\n" +
                "import " + basePkg + ".common.core.util.page.PageRes;\n" +
                "import " + basePkg + ".common.data.util.page.PageUtil;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".mapper." + className + "Mapper;\n" +
                "import " + basePkg + "." + childPkg + ".entity." + childClassName + ";\n" +
                "import " + basePkg + "." + childPkg + ".mapper." + childClassName + "Mapper;\n" +
                "import com.baomidou.mybatisplus.spring.service.IService;\n" +
                "import org.springframework.stereotype.Service;\n" +
                "import org.springframework.transaction.annotation.Transactional;\n\n" +
                "import java.util.List;\n\n" +
                "@Service\n" +
                "public class " + className + "Service extends BaseService<" + className + "Mapper, " + className + "> {\n\n" +
                "    @jakarta.annotation.Resource\n" +
                "    private " + childClassName + "Mapper " + uncapitalize(childClassName) + "Mapper;\n\n" +
                "    public PageRes<" + className + "> page(" + className + " param, PageReq pageReq) {\n" +
                "        LambdaQueryWrapper<" + className + "> queryWrapper = new LambdaQueryWrapper<>();\n" +
                "        queryWrapper.orderByDesc(" + className + "::getCreateTime);\n" +
                "        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));\n" +
                "    }\n\n" +
                "    @Transactional(rollbackFor = Exception.class)\n" +
                "    public void createWithDetails(" + className + " master, List<" + childClassName + "> details) {\n" +
                "        baseMapper.insert(master);\n" +
                "        for (" + childClassName + " detail : details) {\n" +
                "            detail.set" + capitalize(childFieldKey) + "(master.getId());\n" +
                "            " + uncapitalize(childClassName) + "Mapper.insert(detail);\n" +
                "        }\n" +
                "    }\n\n" +
                "    @Transactional(rollbackFor = Exception.class)\n" +
                "    public void updateWithDetails(" + className + " master, List<" + childClassName + "> details) {\n" +
                "        baseMapper.updateById(master);\n" +
                "        LambdaQueryWrapper<" + childClassName + "> deleteWrapper = new LambdaQueryWrapper<>();\n" +
                "        deleteWrapper.eq(" + childClassName + "::get" + capitalize(childFieldKey) + ", master.getId());\n" +
                "        " + uncapitalize(childClassName) + "Mapper.delete(deleteWrapper);\n" +
                "        for (" + childClassName + " detail : details) {\n" +
                "            detail.setId(null);\n" +
                "            detail.set" + capitalize(childFieldKey) + "(master.getId());\n" +
                "            " + uncapitalize(childClassName) + "Mapper.insert(detail);\n" +
                "        }\n" +
                "    }\n\n" +
                "    @Transactional(rollbackFor = Exception.class)\n" +
                "    public void deleteWithDetails(Long id) {\n" +
                "        baseMapper.deleteById(id);\n" +
                "        LambdaQueryWrapper<" + childClassName + "> deleteWrapper = new LambdaQueryWrapper<>();\n" +
                "        deleteWrapper.eq(" + childClassName + "::get" + capitalize(childFieldKey) + ", id);\n" +
                "        " + uncapitalize(childClassName) + "Mapper.delete(deleteWrapper);\n" +
                "    }\n\n" +
                "    public List<" + childClassName + "> getDetails(Long masterId) {\n" +
                "        LambdaQueryWrapper<" + childClassName + "> queryWrapper = new LambdaQueryWrapper<>();\n" +
                "        queryWrapper.eq(" + childClassName + "::get" + capitalize(childFieldKey) + ", masterId);\n" +
                "        return " + uncapitalize(childClassName) + "Mapper.selectList(queryWrapper);\n" +
                "    }\n" +
                "}\n";
    }

    private String generateMasterController(String className, String tableName, String basePkg,
                                             String packageName, String author, String tableComment,
                                             String childTableName, String childForeignKey, String childPackageName) {
        String childClassName = toCamelCase(childTableName, true);
        String childPkg = (childPackageName != null && !childPackageName.isEmpty()) ? childPackageName : packageName;
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);
        String url = "/" + frontendModule + "/" + pageName;
        String perm = frontendModule + ":" + pageName + ":";

        return "/**\n" +
                " * " + tableComment + "控制器（主子表）\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "package " + basePkg + "." + packageName + ".controller;\n\n" +
                "import " + basePkg + ".log.annotation.SLog;\n" +
                "import " + basePkg + ".security.annotation.RateLimit;\n" +
                "import " + basePkg + ".security.annotation.DistributedLock;\n" +
                "import " + basePkg + ".common.core.base.BaseConstant;\n" +
                "import " + basePkg + ".common.data.base.BaseController;\n" +
                "import " + basePkg + ".common.web.domain.dto.R;\n" +
                "import " + basePkg + ".common.core.util.page.PageReq;\n" +
                "import " + basePkg + ".common.core.util.page.PageRes;\n" +
                "import " + basePkg + "." + packageName + ".entity." + className + ";\n" +
                "import " + basePkg + "." + packageName + ".service." + className + "Service;\n" +
                "import " + basePkg + "." + childPkg + ".entity." + childClassName + ";\n" +
                "import cn.dev33.satoken.annotation.SaCheckPermission;\n" +
                "import io.swagger.v3.oas.annotations.Operation;\n" +
                "import io.swagger.v3.oas.annotations.tags.Tag;\n" +
                "import org.springdoc.core.annotations.ParameterObject;\n" +
                "import jakarta.validation.Valid;\n" +
                "import org.springframework.web.bind.annotation.*;\n\n" +
                "import java.util.List;\n\n" +
                "@Tag(name = \"" + className + "\")\n" +
                "@RestController\n" +
                "@RequestMapping(\"" + url + "\")\n" +
                "public class " + className + "Controller extends BaseController<" + className + "Service, " + className + "> {\n\n" +
                "    @GetMapping(\"/page\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"分页查询\")\n" +
                "    public R<PageRes<" + className + ">> page(@ModelAttribute " + className + " param, @ParameterObject PageReq pageReq) {\n" +
                "        return R.success(service.page(param, pageReq));\n" +
                "    }\n\n" +
                "    @GetMapping(\"/detail/{id}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"根据ID获取详情\")\n" +
                "    public R<" + className + "> detail(@PathVariable(\"id\") Long id) {\n" +
                "        return R.success(service.getById(id));\n" +
                "    }\n\n" +
                "    @GetMapping(\"/details/{masterId}\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "view\")\n" +
                "    @Operation(summary = \"获取子表数据\")\n" +
                "    public R<List<" + childClassName + ">> details(@PathVariable(\"masterId\") Long masterId) {\n" +
                "        return R.success(service.getDetails(masterId));\n" +
                "    }\n\n" +
                "    @PostMapping(\"/create\")\n" +
                "    @Operation(summary = \"新增（含子表）\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "add\")\n" +
                "    @SLog(title = \"新增" + className + "\")\n" +
                "    @RateLimit(value = \"default\")\n" +
                "    public R<String> create(@RequestBody @Valid " + className + "CreateRequest request) {\n" +
                "        service.createWithDetails(request.getMaster(), request.getDetails());\n" +
                "        return R.successMsg(\"新增成功\");\n" +
                "    }\n\n" +
                "    @PutMapping(\"/update\")\n" +
                "    @Operation(summary = \"修改（含子表）\")\n" +
                "    @SLog(title = \"修改" + className + "\")\n" +
                "    @DistributedLock(key = \"'" + perm + "' + #request.master.id\", expireTime = 10000)\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "update\")\n" +
                "    public R<String> update(@RequestBody @Valid " + className + "CreateRequest request) {\n" +
                "        service.updateWithDetails(request.getMaster(), request.getDetails());\n" +
                "        return R.successMsg(\"修改成功\");\n" +
                "    }\n\n" +
                "    @DeleteMapping(\"/{id}\")\n" +
                "    @Operation(summary = \"删除（含子表）\")\n" +
                "    @SLog(title = \"删除" + className + "\")\n" +
                "    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = \"" + perm + "delete\")\n" +
                "    public R<String> delete(@PathVariable(\"id\") Long id) {\n" +
                "        service.deleteWithDetails(id);\n" +
                "        return R.successMsg(\"删除成功\");\n" +
                "    }\n\n" +
                "    @lombok.Data\n" +
                "    public static class " + className + "CreateRequest {\n" +
                "        private " + className + " master;\n" +
                "        private List<" + childClassName + "> details;\n" +
                "    }\n" +
                "}\n";
    }

    private String generateMasterDetailFrontendIndexPage(String className, String pageName, List<ColumnInfo> columns,
                                                          String frontendModule, String tableComment, String author,
                                                          String childTableName, String childForeignKey, String childPackageName) {
        String childClassName = toCamelCase(childTableName, true);
        String childPageName = uncapitalize(childClassName);
        String childPkg = (childPackageName != null && !childPackageName.isEmpty()) ? childPackageName : frontendModule;
        String childFieldKey = toCamelCase(childForeignKey, false);

        List<ColumnInfo> displayColumns = columns.stream()
                .filter(c -> !isBaseColumn(c.name))
                .toList();

        List<ColumnInfo> searchColumns = displayColumns.stream()
                .filter(c -> "String".equals(toJavaType(c.type)))
                .limit(2)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append(" * ").append(tableComment).append("主子表页面\n");
        sb.append(" *\n");
        sb.append(" * @author ").append(author).append("\n");
        sb.append(" * @date ").append(java.time.LocalDate.now()).append("\n");
        sb.append(" -->\n");
        sb.append("<template>\n");
        sb.append("  <div class=\"").append(pageName).append("-page\">\n");
        sb.append("    <t-card class=\"search-card\">\n");
        sb.append("      <t-form :data=\"searchForm\" layout=\"inline\" @submit=\"handleSearch\" @reset=\"resetSearch\">\n");
        for (ColumnInfo col : searchColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            sb.append("        <t-form-item label=\"").append(label).append("\" name=\"").append(fieldName).append("\">\n");
            sb.append("          <t-input v-model=\"searchForm.").append(fieldName)
                    .append("\" placeholder=\"请输入").append(label).append("\" clearable />\n");
            sb.append("        </t-form-item>\n");
        }
        sb.append("        <t-form-item>\n");
        sb.append("          <t-button theme=\"default\" type=\"reset\">重置</t-button>\n");
        sb.append("          <t-button theme=\"primary\" type=\"submit\">查询</t-button>\n");
        sb.append("        </t-form-item>\n");
        sb.append("      </t-form>\n");
        sb.append("    </t-card>\n\n");
        sb.append("    <t-card>\n");
        sb.append("      <template #header>\n");
        sb.append("        <div class=\"flex-between\">\n");
        sb.append("          <div></div>\n");
        sb.append("          <t-button theme=\"primary\" @click=\"add\">\n");
        sb.append("            <template #icon><t-icon name=\"add\" /></template>\n");
        sb.append("            新增\n");
        sb.append("          </t-button>\n");
        sb.append("        </div>\n");
        sb.append("      </template>\n\n");
        sb.append("      <t-table\n");
        sb.append("        :loading=\"loading\"\n");
        sb.append("        :data=\"pageParams.list\"\n");
        sb.append("        :columns=\"columns\"\n");
        sb.append(" row-key=\"id\"\n");
        sb.append("        @row-click=\"handleRowClick\"\n");
        sb.append("      />\n\n");
        sb.append("      <div class=\"pagination-container\">\n");
        sb.append("        <t-pagination\n");
        sb.append("          :current=\"pageParams.pageNum\"\n");
        sb.append("          :page-size=\"pageParams.pageSize\"\n");
        sb.append("          :total=\"pageParams.total\"\n");
        sb.append("          :page-size-options=\"[10, 20, 50, 100]\"\n");
        sb.append("          show-jumper\n");
        sb.append("          @current-change=\"handlePageChange\"\n");
        sb.append("          @page-size-change=\"handleSizeChange\"\n");
        sb.append("        />\n");
        sb.append("      </div>\n");
        sb.append("    </t-card>\n\n");
        sb.append("    <t-card v-if=\"selectedRow\" style=\"margin-top: 16px\">\n");
        sb.append("      <template #header>\n");
        sb.append("        <div class=\"flex-between\">\n");
        sb.append("          <div>子表数据 - {{ selectedRow.id }}</div>\n");
        sb.append("        </div>\n");
        sb.append("      </template>\n");
        sb.append("      <t-table :data=\"detailList\" :columns=\"detailColumns\" row-key=\"id\" />\n");
        sb.append("    </t-card>\n\n");
        sb.append("    <save-dialog\n");
        sb.append("      v-if=\"dialog.save\"\n");
        sb.append("      :dialog-visible=\"dialog.save\"\n");
        sb.append("      :edit-data=\"editData\"\n");
        sb.append("      @close=\"dialog.save = false\"\n");
        sb.append("      @saved=\"handleSaved\"\n");
        sb.append("    />\n");
        sb.append("  </div>\n");
        sb.append("</template>\n\n");
        sb.append("<script setup>\n");
        sb.append("import { ref, reactive, onMounted } from 'vue';\n");
        sb.append("import { DialogPlugin, MessagePlugin } from 'tdesign-vue-next';\n");
        sb.append("import ").append(frontendModule).append("Api from '@/api/").append(frontendModule).append("';\n");
        sb.append("import saveDialog from './save.vue';\n\n");
        sb.append("const loading = ref(false);\n");
        sb.append("const dialog = ref({ save: false });\n");
        sb.append("const editData = ref(null);\n");
        sb.append("const selectedRow = ref(null);\n");
        sb.append("const detailList = ref([]);\n");
        sb.append("const searchForm = reactive({});\n");
        sb.append("const pageParams = reactive({ list: [], total: 0, pageNum: 1, pageSize: 10 });\n\n");
        sb.append("const columns = [\n");
        for (ColumnInfo col : displayColumns) {
            String label = col.comment.isEmpty() ? col.name : col.comment;
            String fieldName = toCamelCase(col.name, false);
            sb.append("  { colKey: '").append(fieldName).append("', title: '").append(label).append("' },\n");
        }
        sb.append("  { colKey: 'op', title: '操作', cell: 'op' },\n");
        sb.append("];\n\n");
        sb.append("const detailColumns = [\n");
        sb.append("  { colKey: 'id', title: 'ID' },\n");
        sb.append("];\n\n");
        sb.append("const fetchData = async () => {\n");
        sb.append("  loading.value = true;\n");
        sb.append("  try {\n");
        sb.append("    const res = await ").append(frontendModule).append("Api.").append(pageName).append(".page(searchForm, { pageNo: pageParams.pageNum, pageSize: pageParams.pageSize });\n");
        sb.append("    const data = res.data;\n");
        sb.append("    pageParams.list = data.list || [];\n");
        sb.append("    pageParams.total = data.total || 0;\n");
        sb.append("  } finally {\n");
        sb.append("    loading.value = false;\n");
        sb.append("  }\n");
        sb.append("};\n\n");
        sb.append("const handleRowClick = async ({ row }) => {\n");
        sb.append("  selectedRow.value = row;\n");
        sb.append("  const res = await ").append(frontendModule).append("Api.").append(pageName).append(".details(row.id);\n");
        sb.append("  detailList.value = res.data || [];\n");
        sb.append("};\n\n");
        sb.append("const add = () => {\n");
        sb.append("  editData.value = null;\n");
        sb.append("  dialog.value.save = true;\n");
        sb.append("};\n\n");
        sb.append("const edit = (row) => {\n");
        sb.append("  editData.value = row;\n");
        sb.append("  dialog.value.save = true;\n");
        sb.append("};\n\n");
        sb.append("const handleDelete = (row) => {\n");
        sb.append("  const dialog = DialogPlugin.confirm({\n");
        sb.append("    header: '确认删除',\n");
        sb.append("    body: '删除主表数据将同时删除关联的子表数据，确定要删除吗？',\n");
        sb.append("    onConfirm: async () => {\n");
        sb.append("      await ").append(frontendModule).append("Api.").append(pageName).append(".delete(row.id);\n");
        sb.append("      MessagePlugin.success('删除成功');\n");
        sb.append("      fetchData();\n");
        sb.append("      dialog.destroy();\n");
        sb.append("    },\n");
        sb.append("  });\n");
        sb.append("};\n\n");
        sb.append("const handleSearch = () => { pageParams.pageNum = 1; fetchData(); };\n");
        sb.append("const resetSearch = () => { Object.keys(searchForm).forEach(k => searchForm[k] = ''); handleSearch(); };\n");
        sb.append("const handlePageChange = (curr) => { pageParams.pageNum = curr; fetchData(); };\n");
        sb.append("const handleSizeChange = (size) => { pageParams.pageSize = size; pageParams.pageNum = 1; fetchData(); };\n");
        sb.append("const handleSaved = () => { dialog.value.save = false; fetchData(); };\n\n");
        sb.append("onMounted(() => { fetchData(); });\n");
        sb.append("</script>\n");
        return sb.toString();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public String saveRouterConfig(String tableName, String packageName, boolean overwrite) {
        String frontendRoot = getFrontendProjectRoot();
        if (frontendRoot == null) {
            return null;
        }
        String frontendModule = getFrontendModule(StrUtil.isNotBlank(packageName) ? packageName : "demo");
        return saveRouterConfig(frontendRoot + "/src/router/modules/" + frontendModule + ".ts",
                tableName, packageName, overwrite);
    }

    /** 路由文件路径由调用方给出（清单构建器的 absolutePath），避免两边各自拼路径 */
    String saveRouterConfig(String routerFilePath, String tableName, String packageName, boolean overwrite) {
        String frontendModule = getFrontendModule(StrUtil.isNotBlank(packageName) ? packageName : "demo");
        String className = toCamelCase(tableName, true);
        String pageName = uncapitalize(className);
        String tableComment = getTableComment(tableName);
        File routerFile = new File(routerFilePath);
        String newRoute = buildRouteBlock(className, pageName, frontendModule, tableComment);

        if (routerFile.exists()) {
            return appendToRouterFile(routerFile, newRoute, pageName, overwrite);
        } else {
            return createRouterFile(routerFile, frontendModule, tableComment, className, pageName);
        }
    }

    /** 路由子项（4 空格缩进）：save-local 与 zip 共用同一份模板 */
    static String buildRouteBlock(String className, String pageName, String frontendModule, String tableComment) {
        return "    {\n" +
                "      path: '" + pageName + "',\n" +
                "      name: '" + className + "',\n" +
                "      component: () => import('@/pages/" + frontendModule + "/" + pageName + "/index.vue'),\n" +
                "      meta: {\n" +
                "        title: {\n" +
                "          zh_CN: '" + tableComment + "',\n" +
                "          en_US: '" + className + "',\n" +
                "        },\n" +
                "      },\n" +
                "    },\n";
    }

    /** 完整的路由模块文件内容：save-local 新建与 zip 条目共用 */
    static String buildRouterFileContent(String frontendModule, String pageName, String tableComment, String routeBlock) {
        return "import { AppIcon } from 'tdesign-icons-vue-next';\n" +
                "import { shallowRef } from 'vue';\n\n" +
                "import Layout from '@/layouts/index.vue';\n\n" +
                "export default [\n" +
                "  {\n" +
                "    path: '/" + frontendModule + "',\n" +
                "    component: Layout,\n" +
                "    redirect: '/" + frontendModule + "/" + pageName + "',\n" +
                "    name: '" + frontendModule + "',\n" +
                "    meta: {\n" +
                "      title: {\n" +
                "        zh_CN: '" + tableComment + "',\n" +
                "        en_US: '" + frontendModule.substring(0, 1).toUpperCase() + frontendModule.substring(1) + "',\n" +
                "      },\n" +
                "      icon: shallowRef(AppIcon),\n" +
                "      orderNo: 99,\n" +
                "    },\n" +
                "    children: [\n" +
                routeBlock +
                "    ],\n" +
                "  },\n" +
                "] satisfies RouteRecordRaw[];\n";
    }

    private String createRouterFile(File routerFile, String frontendModule, String tableComment,
                                    String className, String pageName) {
        String content = buildRouterFileContent(frontendModule, pageName, tableComment,
                buildRouteBlock(className, pageName, frontendModule, tableComment));
        writeFile(routerFile.getAbsolutePath(), content);
        return routerFile.getAbsolutePath();
    }

    private String appendToRouterFile(File routerFile, String newRoute, String pageName, boolean overwrite) {
        try {
            String content = Files.readString(routerFile.toPath(), StandardCharsets.UTF_8);

            int existingIdx = findRouteInChildren(content, pageName);
            if (existingIdx >= 0) {
                if (!overwrite) {
                    return null;
                }
                int[] range = findRouteBlockRange(content, existingIdx);
                if (range != null) {
                    String before = content.substring(0, range[0]);
                    String after = content.substring(range[1]);
                    content = before + newRoute.trim() + after;
                    Files.writeString(routerFile.toPath(), content, StandardCharsets.UTF_8);
                    return routerFile.getAbsolutePath();
                }
                return null;
            }

            int childrenIdx = content.indexOf("children: [");
            if (childrenIdx < 0) {
                return null;
            }

            int bracketStart = content.indexOf("[", childrenIdx);
            if (bracketStart < 0) {
                return null;
            }

            int depth = 0;
            int closeIdx = -1;
            for (int i = bracketStart; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        closeIdx = i;
                        break;
                    }
                }
            }

            if (closeIdx < 0) {
                return null;
            }

            String before = content.substring(0, closeIdx);
            String after = content.substring(closeIdx);

            String indent = "\n    ";
            String newContent = before + indent + newRoute.trim() + after;

            Files.writeString(routerFile.toPath(), newContent, StandardCharsets.UTF_8);
            return routerFile.getAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("Failed to update router file: " + routerFile.getAbsolutePath(), e);
        }
    }

    private int findRouteInChildren(String content, String pageName) {
        String marker = "path: '" + pageName + "'";
        int idx = content.indexOf(marker);
        if (idx < 0) {
            return -1;
        }
        int childrenIdx = content.indexOf("children: [");
        if (childrenIdx < 0 || idx < childrenIdx) {
            return -1;
        }
        return idx;
    }

    private int[] findRouteBlockRange(String content, int pathIdx) {
        int blockStart = -1;
        for (int i = pathIdx; i >= 0; i--) {
            if (content.charAt(i) == '{') {
                blockStart = i;
                break;
            }
        }
        if (blockStart < 0) {
            return null;
        }

        int depth = 0;
        int blockEnd = -1;
        for (int i = blockStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    blockEnd = i + 1;
                    break;
                }
            }
        }
        if (blockEnd < 0) {
            return null;
        }

        if (blockEnd < content.length() && content.charAt(blockEnd) == ',') {
            blockEnd++;
        }
        while (blockEnd < content.length() && Character.isWhitespace(content.charAt(blockEnd))) {
            blockEnd++;
        }

        return new int[]{blockStart, blockEnd};
    }

    private String saveApiToFile(String apiPath, String tableName, String packageName, String author, boolean overwrite) {
        String apiName = uncapitalize(toCamelCase(tableName, true));
        String frontendModule = getFrontendModule(packageName);
        String url = "/" + frontendModule + "/" + apiName;
        String tableComment = getTableComment(tableName);

        String newApiBlock = "/**\n" +
                " * " + tableComment + " API\n" +
                " *\n" +
                " * @author " + author + "\n" +
                " * @date " + java.time.LocalDate.now() + "\n" +
                " */\n" +
                "const " + apiName + " = createCrud('" + url + "');\n";

        File apiFile = new File(apiPath);
        if (!apiFile.exists()) {
            String content = "import { createCrud, http } from '@/api/core/crud';\n\n" +
                    newApiBlock + "\n" +
                    "export default { " + apiName + " };\n";
            writeFile(apiPath, content);
            return apiPath;
        }

        try {
            String content = Files.readString(apiFile.toPath(), StandardCharsets.UTF_8);
            String marker = "const " + apiName + " = createCrud(";

            if (content.contains(marker)) {
                if (!overwrite) {
                    return null;
                }
                int start = content.indexOf(marker);
                int blockStart = content.lastIndexOf("/**", start);
                if (blockStart < 0) {
                    blockStart = start;
                }
                int blockEnd = content.indexOf("\n", start);
                while (blockEnd < content.length() - 1 && !content.substring(blockEnd + 1).trim().startsWith("const ")
                        && !content.substring(blockEnd + 1).trim().startsWith("export ")
                        && !content.substring(blockEnd + 1).trim().startsWith("/**")) {
                    blockEnd = content.indexOf("\n", blockEnd + 1);
                    if (blockEnd < 0) {
                        blockEnd = content.length();
                        break;
                    }
                }
                blockEnd++;
                String before = content.substring(0, blockStart);
                String after = content.substring(blockEnd);
                content = before + newApiBlock.trim() + "\n" + after;
                Files.writeString(apiFile.toPath(), content, StandardCharsets.UTF_8);
                return apiPath;
            }

            int exportIdx = content.lastIndexOf("export default {");
            if (exportIdx < 0) {
                return null;
            }

            int insertIdx = exportIdx;
            int prevNewline = content.lastIndexOf("\n", exportIdx - 1);
            if (prevNewline > 0) {
                insertIdx = prevNewline;
            }

            String before = content.substring(0, insertIdx);
            String after = content.substring(insertIdx);

            int openBrace = after.indexOf("{");
            int closeBrace = after.lastIndexOf("}");
            if (openBrace < 0 || closeBrace < 0) {
                return null;
            }

            String beforeBrace = after.substring(0, closeBrace);
            String afterBrace = after.substring(closeBrace);
            String existingExports = beforeBrace.substring(beforeBrace.lastIndexOf("{") + 1).trim();
            String newExports;
            if (existingExports.isEmpty()) {
                newExports = apiName;
            } else {
                newExports = existingExports + ", " + apiName;
            }
            String newExportPart = beforeBrace.substring(0, beforeBrace.lastIndexOf("{") + 1) + " " + newExports + " ";

            content = before + "\n" + newApiBlock + newExportPart + afterBrace;
            Files.writeString(apiFile.toPath(), content, StandardCharsets.UTF_8);
            return apiPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to update API file: " + apiPath, e);
        }
    }

    public boolean checkRouteExists(String tableName, String packageName) {
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(toCamelCase(tableName, true));
        String frontendRoot = getFrontendProjectRoot();
        if (frontendRoot == null) {
            return false;
        }
        String routerFilePath = frontendRoot + "/src/router/modules/" + frontendModule + ".ts";
        File routerFile = new File(routerFilePath);
        if (!routerFile.exists()) {
            return false;
        }
        try {
            String content = Files.readString(routerFile.toPath(), StandardCharsets.UTF_8);
            return findRouteInChildren(content, pageName) >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    public boolean checkApiExists(String tableName, String packageName) {
        String frontendModule = getFrontendModule(packageName);
        String apiName = uncapitalize(toCamelCase(tableName, true));
        String frontendRoot = getFrontendProjectRoot();
        if (frontendRoot == null) {
            return false;
        }
        String apiFilePath = frontendRoot + "/src/api/" + frontendModule + ".js";
        File apiFile = new File(apiFilePath);
        if (!apiFile.exists()) {
            return false;
        }
        try {
            String content = Files.readString(apiFile.toPath(), StandardCharsets.UTF_8);
            String marker = "const " + apiName + " = createCrud(";
            return content.contains(marker);
        } catch (IOException e) {
            return false;
        }
    }

    private String toCamelCase(String name, boolean upperFirst) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = upperFirst;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String toKebabCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    private String uncapitalize(String str) {
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    private String extractModule(String tableName) {
        if (tableName.contains("_")) {
            return tableName.substring(0, tableName.indexOf('_'));
        }
        return tableName;
    }

    private String toJavaType(String sqlType) {
        return switch (sqlType.toUpperCase()) {
            case "BIGINT" -> "Long";
            case "INT", "INTEGER", "TINYINT", "SMALLINT" -> "Integer";
            case "VARCHAR", "CHAR", "TEXT", "LONGTEXT", "MEDIUMTEXT" -> "String";
            case "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE" -> "java.math.BigDecimal";
            case "DATETIME", "TIMESTAMP" -> "java.time.LocalDateTime";
            case "DATE" -> "java.time.LocalDate";
            case "TIME" -> "java.time.LocalTime";
            case "BOOLEAN", "BIT" -> "Boolean";
            default -> "String";
        };
    }

    private boolean isBaseColumn(String name) {
        return Set.of("id", "tenant_id", "dept_id", "create_by", "create_time", "update_by", "update_time").contains(name);
    }

    private Map<Integer, String> parseStatusOptions(String comment) {
        if (comment == null || comment.isEmpty()) return null;
        int idx = comment.indexOf("：");
        if (idx < 0) idx = comment.indexOf(":");
        if (idx < 0) return null;
        String optionsStr = comment.substring(idx + 1).trim();
        if (optionsStr.isEmpty()) return null;
        String[] parts = optionsStr.split("[\\s,，]+");
        Map<Integer, String> options = new LinkedHashMap<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int dashIdx = part.indexOf('-');
            if (dashIdx <= 0) return null;
            try {
                Integer key = Integer.parseInt(part.substring(0, dashIdx).trim());
                String value = part.substring(dashIdx + 1).trim();
                if (value.isEmpty()) return null;
                options.put(key, value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return options.isEmpty() ? null : options;
    }

    private boolean isTextType(String sqlType) {
        if (sqlType == null) return false;
        return switch (sqlType.toUpperCase()) {
            case "TEXT", "MEDIUMTEXT", "LONGTEXT", "TINYTEXT" -> true;
            default -> false;
        };
    }

    private String extractShortComment(String comment) {
        if (comment == null) return "";
        int idx = comment.indexOf("：");
        if (idx < 0) idx = comment.indexOf(":");
        if (idx > 0) return comment.substring(0, idx).trim();
        return comment;
    }

    private boolean needsSize(String sqlType) {
        return !Set.of("TEXT", "LONGTEXT", "MEDIUMTEXT", "DATETIME", "TIMESTAMP", "DATE", "TIME", "BOOLEAN", "BIT")
                .contains(sqlType.toUpperCase());
    }

    public record TableInfo(String tableName, String comment) {}
    public record ColumnInfo(String name, String type, int size, String comment) {}
    public record GeneratedCode(String entity, String mapper, String service, String controller, String sql,
                                 String frontendApi, String frontendIndexPage, String frontendSavePage,
                                 String frontendRouter, String dtoRequest, String dtoQueryRequest,
                                 String menuSql,
                                 String childEntity, String childMapper, String childService,
                                 String childController, String childFrontendApi,
                                 String childFrontendIndexPage, String childFrontendSavePage,
                                 String genType) {

        public GeneratedCode(String entity, String mapper, String service, String controller, String sql,
                             String frontendApi, String frontendIndexPage, String frontendSavePage,
                             String frontendRouter, String dtoRequest, String dtoQueryRequest,
                             String menuSql) {
            this(entity, mapper, service, controller, sql, frontendApi, frontendIndexPage, frontendSavePage,
                    frontendRouter, dtoRequest, dtoQueryRequest, menuSql,
                    null, null, null, null, null, null, null, null);
        }

        public GeneratedCode(String entity, String mapper, String service, String controller, String sql,
                             String frontendApi, String frontendIndexPage, String frontendSavePage,
                             String frontendRouter, String dtoRequest, String dtoQueryRequest,
                             String menuSql,
                             String childEntity, String childMapper, String childService,
                             String childController, String childFrontendApi,
                             String childFrontendIndexPage, String childFrontendSavePage) {
            this(entity, mapper, service, controller, sql, frontendApi, frontendIndexPage, frontendSavePage,
                    frontendRouter, dtoRequest, dtoQueryRequest, menuSql,
                    childEntity, childMapper, childService, childController, childFrontendApi,
                    childFrontendIndexPage, childFrontendSavePage, null);
        }
    }

    /**
     * 代码文件清单条目：{@code saveToLocal} 与 {@code buildZip} 的唯一路径来源。
     * <p>
     * {@code relativePath} 相对「后端仓与前端仓的同级父目录」，ZIP 条目名即它；
     * {@code absolutePath} 是本地写盘用的绝对路径（前端仓未解析到时为 {@code null}）。
     */
    public record FileEntry(String type, String relativePath, String absolutePath, String content) {}

    public static boolean isRunningFromJar() {
        return !isLocalDevMode();
    }

    private static Boolean cachedLocalDevMode = null;

    private static boolean isLocalDevMode() {
        if (cachedLocalDevMode != null) return cachedLocalDevMode;
        cachedLocalDevMode = detectProjectRootFromUserDir() != null;
        return cachedLocalDevMode;
    }

    private static String detectProjectRootFromUserDir() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null) return null;
        File dir = new File(userDir);
        if (isValidProjectRoot(dir)) return userDir;
        File parent = dir.getParentFile();
        if (parent != null && isValidProjectRoot(parent)) return parent.getAbsolutePath();
        return null;
    }

    private static boolean isValidProjectRoot(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        boolean hasBiz = new File(dir, "jffast-biz").isDirectory();
        boolean hasPom = new File(dir, "pom.xml").exists();
        return hasBiz && hasPom;
    }

    public static String getProjectRoot() {
        String fromClasspath = getProjectRootFromClasspath();
        if (fromClasspath != null) return fromClasspath;
        String fromUserDir = detectProjectRootFromUserDir();
        if (fromUserDir != null) return fromUserDir;
        return System.getProperty("user.dir");
    }

    private static String getProjectRootFromClasspath() {
        try {
            URL location = CodeGenerator.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            File classDir = new File(location.getPath());
            if (classDir.isDirectory() && classDir.getName().equals("classes")) {
                File targetDir = classDir.getParentFile();
                if (targetDir != null && targetDir.getName().equals("target")) {
                    File moduleDir = targetDir.getParentFile();
                    if (moduleDir != null) {
                        File bizDir = moduleDir.getParentFile();
                        if (bizDir != null && bizDir.getName().equals("jffast-biz")) {
                            File root = bizDir.getParentFile();
                            if (root != null) return root.getAbsolutePath();
                        }
                        File startersDir = moduleDir.getParentFile();
                        if (startersDir != null && startersDir.getName().equals("jffast-starters")) {
                            File root = startersDir.getParentFile();
                            if (root != null) return root.getAbsolutePath();
                        }
                        File root = moduleDir.getParentFile();
                        if (root != null) return root.getAbsolutePath();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 项目形态：base（本开源版仅支持 base；探测逻辑保留以便扩展形态复用）。
     * 例外约定：目录探测本身可保留，不在此硬编码私有构件名。
     */
    public static String detectProjectType() {
        String projectRoot = getProjectRoot();
        File rootDir = new File(projectRoot);
        if (new File(rootDir, "jffast-biz").isDirectory()) {
            return "base";
        }
        return "base";
    }

    /** 模块命名前缀 */
    public static String getNamingPrefix() {
        return "jffast-";
    }

    /** 业务模块聚合目录名 */
    public static String getBizDirName() {
        return "jffast-biz";
    }

    private static String getBizDir(String projectRoot) {
        return projectRoot + "/" + getBizDirName();
    }

    private static String getBizModuleDir(String projectRoot, String packageName, String moduleName) {
        File bizDir = new File(getBizDir(projectRoot));
        return new File(bizDir, resolveModuleDirName(bizDir, packageName, moduleName)).getAbsolutePath();
    }

    /**
     * 解析业务模块目录名（不含父路径）。优先复用磁盘上的真实模块，找不到时回退到按命名约定推导的名字，
     * 不再回退到 {@code projectRoot} 或任意第一个子目录——那会把生成代码写到仓根或别的模块里。
     */
    private static String resolveModuleDirName(File bizDir, String packageName, String moduleName) {
        String namingPrefix = getNamingPrefix();
        if (moduleName != null && !moduleName.isEmpty()) {
            for (String candidate : new String[]{moduleName, namingPrefix + moduleName}) {
                if (new File(bizDir, candidate).isDirectory()) {
                    return candidate;
                }
            }
        }
        String pkg = StrUtil.isNotBlank(packageName) ? packageName : "demo";
        String guessedName = namingPrefix + pkg;
        if (new File(bizDir, guessedName).isDirectory()) {
            return guessedName;
        }
        String systemName = namingPrefix + "system";
        if (new File(bizDir, systemName).isDirectory()) {
            return systemName;
        }
        File[] subDirs = bizDir.listFiles(File::isDirectory);
        if (subDirs != null && subDirs.length > 0) {
            return subDirs[0].getName();
        }
        return guessedName;
    }

    public List<String> listModules() {
        String projectRoot = getProjectRoot();
        String bizDir = getBizDir(projectRoot);
        List<String> modules = new ArrayList<>();
        File bizDirFile = new File(bizDir);
        if (bizDirFile.exists() && bizDirFile.isDirectory()) {
            File[] subDirs = bizDirFile.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File dir : subDirs) {
                    if (new File(dir, "pom.xml").exists()) {
                        modules.add(dir.getName());
                    }
                }
            }
        }
        return modules;
    }

    /**
     * 模块名 / 包名白名单：小写字母开头，仅含小写字母、数字、连字符。
     * <p>
     * 这类值会直接拼进 {@code new File(parent, child)} 与 {@code mkdirs()}，
     * 而 {@code File} 会正常解析 {@code ../} 与路径分隔符 —— 不校验即可在项目目录之外
     * 创建目录并写入 .java / 脚本文件。故在入口处同时做「字符白名单」与「目录边界断言」。
     */
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[a-z][a-z0-9-]*$");

    /** Java 字符串字面量转义：数据库注释含引号 / 反斜杠 / 换行会破坏产物源码 */
    private static String escapeJavaLiteral(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    /** SQL 单引号字面量（及 `--` 行注释）转义：注释含单引号 / 反斜杠 / 换行会破坏建表或菜单语句 */
    private static String escapeSqlLiteral(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("'", "''").replace("\r", " ").replace("\n", " ");
    }

    /** 校验 segment 合法，并把它安全地解析到 baseDir 之下 */
    private static File resolveSafeSegment(File baseDir, String segment, String label) {
        if (StrUtil.isBlank(segment) || !SAFE_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException(
                    label + "非法：只允许小写字母开头，且仅包含小写字母、数字和连字符，实际值=[" + segment + "]");
        }
        try {
            File base = baseDir.getCanonicalFile();
            File target = new File(base, segment).getCanonicalFile();
            if (!target.getPath().startsWith(base.getPath() + File.separator)) {
                throw new SecurityException(label + "路径越界：" + target.getPath());
            }
            return target;
        } catch (IOException e) {
            throw new IllegalStateException("解析" + label + "失败：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> createModule(String moduleName) {
        String projectRoot = getProjectRoot();
        String bizDir = getBizDir(projectRoot);
        String namingPrefix = getNamingPrefix();
        // 先校验再拼接：moduleName 会参与 mkdirs()，缺校验可穿越到项目目录之外写文件
        resolveSafeSegment(new File(bizDir), moduleName, "模块名");
        String fullModuleName = moduleName.startsWith(namingPrefix) ? moduleName : namingPrefix + moduleName;
        File moduleDir = new File(bizDir, fullModuleName);
        if (moduleDir.exists()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "模块已存在: " + fullModuleName);
            return result;
        }

        moduleDir.mkdirs();
        new File(moduleDir, "src/main/java").mkdirs();
        new File(moduleDir, "src/main/resources").mkdirs();

        String pomContent = generateBaseModulePom(fullModuleName, moduleName);
        writeFile(moduleDir.getAbsolutePath() + "/pom.xml", pomContent);

        updateBizPom(bizDir, fullModuleName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "模块创建成功: " + fullModuleName);
        result.put("path", moduleDir.getAbsolutePath());
        result.put("moduleName", fullModuleName);
        return result;
    }

    private String generateBaseModulePom(String artifactId, String moduleName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n\n" +
                "    <parent>\n" +
                "        <groupId>cn.jfspeed</groupId>\n" +
                "        <artifactId>jffast-biz</artifactId>\n" +
                "        <version>1.0.0</version>\n" +
                "        <relativePath>../pom.xml</relativePath>\n" +
                "    </parent>\n\n" +
                "    <artifactId>" + artifactId + "</artifactId>\n" +
                "    <name>" + artifactId + "</name>\n" +
                "    <description>jffast " + moduleName + " module</description>\n\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>cn.jfspeed</groupId>\n" +
                "            <artifactId>jffast-starter-mybatis</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>cn.jfspeed</groupId>\n" +
                "            <artifactId>jffast-starter-web</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>cn.jfspeed</groupId>\n" +
                "            <artifactId>jffast-starter-security</artifactId>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>cn.jfspeed</groupId>\n" +
                "            <artifactId>jffast-starter-log</artifactId>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n\n" +
                "</project>\n";
    }


    private void updateBizPom(String bizDir, String moduleName) {
        File bizPom = new File(bizDir, "pom.xml");
        if (!bizPom.exists()) return;
        try {
            String content = Files.readString(bizPom.toPath(), StandardCharsets.UTF_8);
            if (content.contains("<module>" + moduleName + "</module>")) return;
            String moduleEntry = "        <module>" + moduleName + "</module>";
            content = content.replace("</modules>", moduleEntry + "\n    </modules>");
            writeFile(bizPom.getAbsolutePath(), content);
        } catch (IOException e) {
            // ignore
        }
    }

    public static String getFrontendProjectRoot() {
        File backendDir = new File(getProjectRoot());
        return resolveFrontendRoot(backendDir, System.getProperty("jffast.codegen.frontend-root"));
    }

    /**
     * 解析与当前后端仓同级的那个前端工程目录。
     * <p>
     * 不能写死前端仓名：同级可能存在多个前端仓，写死会把生成的代码落到错误的前端仓。
     * 约定按当前后端仓名推导：{@code xxx-pro} → {@code xxx-ui-pro}，其余 {@code xxx} → {@code xxx-ui}。
     * 非标准布局可用系统属性 {@code jffast.codegen.frontend-root} 显式指定。
     */
    static String resolveFrontendRoot(File backendDir, String configuredFrontendRoot) {
        if (configuredFrontendRoot != null && !configuredFrontendRoot.trim().isEmpty()) {
            File configured = new File(configuredFrontendRoot.trim());
            return configured.isDirectory() ? configured.getAbsolutePath() : null;
        }
        File parentDir = backendDir.getParentFile();
        if (parentDir == null) {
            return null;
        }
        File uiDir = new File(parentDir, frontendDirName(backendDir.getName()));
        return uiDir.isDirectory() ? uiDir.getAbsolutePath() : null;
    }

    /** 前端仓目录名：{@code app} → {@code app-ui}，{@code app-pro} → {@code app-ui-pro} */
    static String frontendDirName(String backendName) {
        if (backendName.endsWith("-pro")) {
            return backendName.substring(0, backendName.length() - "-pro".length()) + "-ui-pro";
        }
        return backendName + "-ui";
    }

    public List<String> getSavePaths(String tableName, String packageName, String moduleName) {
        String className = toCamelCase(tableName, true);
        String basePkg = "cn.jfspeed";
        String projectRoot = getProjectRoot();
        String bizModuleDir = getBizModuleDir(projectRoot, packageName, moduleName);
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);
        String frontendRoot = getFrontendProjectRoot();

        List<String> paths = new ArrayList<>();
        paths.add(bizModuleDir + "/src/main/java/"
                + basePkg.replace('.', '/') + "/" + packageName + "/entity/" + className + ".java");
        paths.add(bizModuleDir + "/src/main/java/"
                + basePkg.replace('.', '/') + "/" + packageName + "/mapper/"
                + className + "Mapper.java");
        paths.add(bizModuleDir + "/src/main/java/"
                + basePkg.replace('.', '/') + "/" + packageName + "/service/"
                + className + "Service.java");
        paths.add(bizModuleDir + "/src/main/java/"
                + basePkg.replace('.', '/') + "/" + packageName + "/controller/"
                + className + "Controller.java");

        if (frontendRoot != null) {
            paths.add(frontendRoot + "/src/api/" + frontendModule + ".js");
            paths.add(frontendRoot + "/src/pages/" + frontendModule + "/" + pageName + "/index.vue");
            paths.add(frontendRoot + "/src/pages/" + frontendModule + "/" + pageName + "/save.vue");
            paths.add(frontendRoot + "/src/router/modules/" + frontendModule + ".ts");
        }

        return paths;
    }

    public Map<String, Object> checkFilesExist(String tableName, String packageName, String moduleName) {
        List<String> paths = getSavePaths(tableName, packageName, moduleName);
        Map<String, Object> result = new LinkedHashMap<>();
        String className = toCamelCase(tableName, true);
        String basePkg = "cn.jfspeed";
        String frontendModule = getFrontendModule(packageName);
        String pageName = uncapitalize(className);

        String[] keys = {"entity", "mapper", "service", "controller", "frontendApi", "frontendIndexPage", "frontendSavePage", "frontendRouter", "menuSql"};
        for (int i = 0; i < keys.length && i < paths.size(); i++) {
            Map<String, Object> info = new HashMap<>();
            File f = new File(paths.get(i));
            info.put("exists", f.exists());

            String classPath = "";
            switch (keys[i]) {
                case "entity":
                    classPath = basePkg + "." + packageName + ".entity." + className;
                    break;
                case "mapper":
                    classPath = basePkg + "." + packageName + ".mapper." + className + "Mapper";
                    break;
                case "service":
                    classPath = basePkg + "." + packageName + ".service." + className + "Service";
                    break;
                case "controller":
                    classPath = basePkg + "." + packageName + ".controller." + className + "Controller";
                    break;
                case "frontendApi":
                    classPath = "src/api/" + frontendModule + ".js";
                    info.put("apiExists", checkApiExists(tableName, packageName));
                    break;
                case "frontendIndexPage":
                    classPath = "src/pages/" + frontendModule + "/" + pageName + "/index.vue";
                    break;
                case "frontendSavePage":
                    classPath = "src/pages/" + frontendModule + "/" + pageName + "/save.vue";
                    break;
                case "frontendRouter":
                    classPath = "src/router/modules/" + frontendModule + ".ts";
                    info.put("routeExists", checkRouteExists(tableName, packageName));
                    break;
                case "menuSql":
                    classPath = "src/main/resources/sql/" + tableName + "_menu.sql";
                    break;
            }
            info.put("path", classPath);
            result.put(keys[i], info);
        }
        return result;
    }

    public Map<String, String> saveToLocal(String tableName, String packageName, String author,
                             List<String> types, boolean overwrite, boolean includeDto) {
        return saveToLocal(tableName, packageName, author, types, overwrite, includeDto, "crud", null, null, null, null, null, null);
    }

    public Map<String, String> saveToLocal(String tableName, String packageName, String author,
                             List<String> types, boolean overwrite, boolean includeDto,
                             String genType, String parentField, String nameField,
                             String childTableName, String childForeignKey, String childPackageName,
                             String moduleName) {
        if (isRunningFromJar()) {
            return null;
        }
        String frontendRoot = getFrontendProjectRoot();
        return saveToLocal(tableName, packageName, author, types, overwrite, includeDto, genType,
                parentField, nameField, childTableName, childForeignKey, childPackageName, moduleName,
                new File(getProjectRoot()), frontendRoot == null ? null : new File(frontendRoot));
    }

    /**
     * 保存到本地（可指定根目录，供测试注入临时工程）：与 {@link #buildManifest} 同源，按清单逐条落盘。
     * <p>
     * API / 路由仍走增量合并（saveApiToFile / saveRouterConfig），其余文件直接写清单内容；
     * 落盘路径全部来自清单的 {@code absolutePath}，不再单独拼路径。
     */
    Map<String, String> saveToLocal(String tableName, String packageName, String author,
                             List<String> types, boolean overwrite, boolean includeDto,
                             String genType, String parentField, String nameField,
                             String childTableName, String childForeignKey, String childPackageName,
                             String moduleName, File projectRoot, File frontendRoot) {
        validateCodegenArgs(projectRoot, tableName, packageName, moduleName, childTableName, childPackageName);
        GeneratedCode code = generate(tableName, packageName, author, includeDto, genType,
                parentField, nameField, childTableName, childForeignKey, childPackageName);
        List<FileEntry> manifest = buildManifest(code, tableName, packageName, author, types, includeDto,
                genType, childTableName, childPackageName, moduleName, projectRoot, frontendRoot);
        Map<String, String> savedPaths = new HashMap<>();
        for (FileEntry entry : manifest) {
            if (entry.absolutePath() == null) {
                continue;
            }
            String type = entry.type();
            String result;
            if ("frontendApi".equals(type) || "childFrontendApi".equals(type)) {
                String apiTable = "childFrontendApi".equals(type) ? childTableName : tableName;
                String apiPackage = "childFrontendApi".equals(type) && StrUtil.isNotBlank(childPackageName)
                        ? childPackageName : packageName;
                result = saveApiToFile(entry.absolutePath(), apiTable, apiPackage, author, overwrite);
            } else if ("frontendRouter".equals(type)) {
                result = saveRouterConfig(entry.absolutePath(), tableName, packageName, overwrite);
            } else {
                saveFile(entry.absolutePath(), entry.content(), overwrite);
                result = entry.absolutePath();
            }
            if (result != null) {
                savedPaths.put(type, result);
            }
        }
        return savedPaths;
    }

    private static void validateCodegenArgs(File projectRoot, String tableName, String packageName,
                                            String moduleName, String childTableName, String childPackageName) {
        File bizDir = new File(getBizDir(normalizePath(projectRoot.getAbsolutePath())));
        resolveSafeSegment(bizDir, tableName, "表名");
        if (StrUtil.isNotBlank(packageName)) {
            resolveSafeSegment(bizDir, packageName, "包名");
        }
        if (StrUtil.isNotBlank(moduleName)) {
            resolveSafeSegment(bizDir, moduleName, "模块名");
        }
        if (StrUtil.isNotBlank(childTableName)) {
            resolveSafeSegment(bizDir, childTableName, "子表名");
        }
        if (StrUtil.isNotBlank(childPackageName)) {
            resolveSafeSegment(bizDir, childPackageName, "子表包名");
        }
    }

    private void saveFile(String path, String content, boolean overwrite) {
        File f = new File(path);
        if (f.exists() && !overwrite) {
            return;
        }
        writeFile(path, content);
    }

    private void writeFile(String path, String content) {
        try {
            File f = new File(path);
            f.getParentFile().mkdirs();
            try (FileWriter w = new FileWriter(f)) {
                w.write(content);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + path, e);
        }
    }

    // ==================== zip 打包（JAR / 容器部署下的「保存到本地」替代方案） ====================

    /**
     * 把生成的代码打包为 zip 字节流。
     * <p>
     * JAR / Docker / K8s 部署下没有可写的本地工程目录，`save-local` 会被拒绝；
     * 此时前端改用本接口一次性下载整个模块（entity / mapper / service / controller /
     * 前端 api / 页面 / 路由 / 菜单 SQL）。
     * <p>
     * 条目名与 {@link #saveToLocal} 的落盘路径同源（都来自 {@link #buildManifest}）：
     * 解压到「后端仓与前端仓的同级父目录」即为覆盖式回灌。
     * <p>
     * 请求结构、Content-Type 与下载文件名语义均未改变，只是 zip 内部布局对齐了本地落点。
     *
     * @return zip 文件内容；条目名与 {@link #getSavePaths} 的相对部分一致
     */
    public byte[] buildZip(String tableName, String packageName, String author,
                           List<String> types, boolean includeDto, String genType,
                           String parentField, String nameField,
                           String childTableName, String childForeignKey, String childPackageName,
                           String moduleName) {
        String frontendRoot = getFrontendProjectRoot();
        return buildZip(tableName, packageName, author, types, includeDto, genType,
                parentField, nameField, childTableName, childForeignKey, childPackageName, moduleName,
                new File(getProjectRoot()), frontendRoot == null ? null : new File(frontendRoot));
    }

    /** 可指定根目录的打包入口（供测试注入临时工程），与 {@link #buildManifest} 同源 */
    byte[] buildZip(String tableName, String packageName, String author,
                    List<String> types, boolean includeDto, String genType,
                    String parentField, String nameField,
                    String childTableName, String childForeignKey, String childPackageName,
                    String moduleName, File projectRoot, File frontendRoot) {
        validateCodegenArgs(projectRoot, tableName, packageName, moduleName, childTableName, childPackageName);
        GeneratedCode code = generate(tableName, packageName, author, includeDto, genType,
                parentField, nameField, childTableName, childForeignKey, childPackageName);
        List<FileEntry> manifest = buildManifest(code, tableName, packageName, author, types, includeDto,
                genType, childTableName, childPackageName, moduleName, projectRoot, frontendRoot);

        // 主子表同模块时父子 API 落在同一个 *.js：save-local 由 saveApiToFile 增量合并，
        // zip 没有增量过程，必须在这里合成一份，否则 ZipOutputStream 会因重复条目直接失败
        Map<String, String> entries = new LinkedHashMap<>();
        for (FileEntry entry : manifest) {
            String content = entry.content() == null ? "" : entry.content();
            String previous = entries.get(entry.relativePath());
            entries.put(entry.relativePath(),
                    previous == null ? content : mergeApiContents(previous, content));
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("打包生成代码失败：" + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    /** 合并两个「独立可用」的 API 文件内容：去掉重复 import / export，只留一个 export default */
    static String mergeApiContents(String first, String second) {
        Pattern exportBlock = Pattern.compile("export default\\s*\\{([^}]*)\\};\\s*$");
        Matcher firstExport = exportBlock.matcher(first);
        Matcher secondExport = exportBlock.matcher(second);
        if (!firstExport.find() || !secondExport.find()) {
            return first + "\n" + second;
        }
        String head = first.substring(0, firstExport.start()).stripTrailing();
        String body = second.substring(0, secondExport.start())
                .replaceFirst("^import [^\\n]*;\\n\\s*", "")
                .strip();
        String exports = (firstExport.group(1).trim() + ", " + secondExport.group(1).trim())
                .replaceAll("\\s+", " ");
        return head + "\n\n" + body + "\n\nexport default { " + exports + " };\n";
    }

    /**
     * 统一的「生成哪些文件、每个文件放到哪个相对路径」清单构建器。
     * <p>
     * {@code saveToLocal} 与 {@code buildZip} 都只消费本方法的结果，不允许再各自拼路径；
     * 以后新增文件类型只改这里，两边不可能再漂。{@code relativePath} 相对后端仓与前端仓的同级父目录
     * （含后端仓名 / 实际解析出的前端仓名），ZIP 条目名即它。
     */
    List<FileEntry> buildManifest(GeneratedCode code, String tableName, String packageName, String author,
                                  List<String> types, boolean includeDto, String genType,
                                  String childTableName, String childPackageName, String moduleName,
                                  File projectRoot, File frontendRoot) {
        String pkg = normalizePackageName(packageName);
        String className = toCamelCase(tableName, true);
        String pageName = uncapitalize(className);
        String frontendModule = getFrontendModule(pkg);
        String tableComment = getTableComment(tableName);

        File bizDir = new File(getBizDir(normalizePath(projectRoot.getAbsolutePath())));
        String moduleDirName = resolveModuleDirName(bizDir, pkg, moduleName);
        String backendRepoName = projectRoot.getName();
        String frontendRepoName = frontendRoot != null ? frontendRoot.getName() : frontendDirName(backendRepoName);

        String backendAbsRoot = normalizePath(projectRoot.getAbsolutePath())
                + "/" + getBizDirName() + "/" + moduleDirName;
        String backendRelRoot = backendRepoName + "/" + getBizDirName() + "/" + moduleDirName;
        String javaAbsRoot = backendAbsRoot + "/src/main/java/cn/jfspeed/" + pkg;
        String javaRelRoot = backendRelRoot + "/src/main/java/cn/jfspeed/" + pkg;
        String webAbsRoot = frontendRoot == null ? null : normalizePath(frontendRoot.getAbsolutePath()) + "/src";
        String webRelRoot = frontendRepoName + "/src";

        boolean all = types == null || types.isEmpty();
        List<FileEntry> entries = new ArrayList<>();

        addFile(entries, all || types.contains("entity"), "entity",
                javaRelRoot + "/entity/" + className + ".java",
                javaAbsRoot + "/entity/" + className + ".java", code.entity());
        addFile(entries, all || types.contains("mapper"), "mapper",
                javaRelRoot + "/mapper/" + className + "Mapper.java",
                javaAbsRoot + "/mapper/" + className + "Mapper.java", code.mapper());
        addFile(entries, all || types.contains("service"), "service",
                javaRelRoot + "/service/" + className + "Service.java",
                javaAbsRoot + "/service/" + className + "Service.java", code.service());
        addFile(entries, all || types.contains("controller"), "controller",
                javaRelRoot + "/controller/" + className + "Controller.java",
                javaAbsRoot + "/controller/" + className + "Controller.java", code.controller());

        addFile(entries, all || types.contains("frontendApi"), "frontendApi",
                webRelRoot + "/api/" + frontendModule + ".js",
                webAbsRoot + "/api/" + frontendModule + ".js", standaloneApiContent(code.frontendApi()));
        addFile(entries, all || types.contains("frontendIndexPage"), "frontendIndexPage",
                webRelRoot + "/pages/" + frontendModule + "/" + pageName + "/index.vue",
                webAbsRoot + "/pages/" + frontendModule + "/" + pageName + "/index.vue", code.frontendIndexPage());
        addFile(entries, all || types.contains("frontendSavePage"), "frontendSavePage",
                webRelRoot + "/pages/" + frontendModule + "/" + pageName + "/save.vue",
                webAbsRoot + "/pages/" + frontendModule + "/" + pageName + "/save.vue", code.frontendSavePage());
        addFile(entries, all || types.contains("frontendRouter"), "frontendRouter",
                webRelRoot + "/router/modules/" + frontendModule + ".ts",
                webAbsRoot + "/router/modules/" + frontendModule + ".ts",
                buildRouterFileContent(frontendModule, pageName, tableComment,
                        buildRouteBlock(className, pageName, frontendModule, tableComment)));

        addFile(entries, all || types.contains("menuSql"), "menuSql",
                backendRelRoot + "/src/main/resources/sql/" + tableName + "_menu.sql",
                backendAbsRoot + "/src/main/resources/sql/" + tableName + "_menu.sql", code.menuSql());

        // dtoRequest 无条件产出：Controller 模板依赖它；includeDto 仅控制 QueryRequest
        if (code.dtoRequest() != null) {
            entries.add(new FileEntry("dtoRequest",
                    javaRelRoot + "/domain/dto/" + className + "Request.java",
                    javaAbsRoot + "/domain/dto/" + className + "Request.java", code.dtoRequest()));
        }
        if (includeDto && code.dtoQueryRequest() != null) {
            entries.add(new FileEntry("dtoQueryRequest",
                    javaRelRoot + "/domain/dto/" + className + "QueryRequest.java",
                    javaAbsRoot + "/domain/dto/" + className + "QueryRequest.java", code.dtoQueryRequest()));
        }

        if ("masterDetail".equals(genType) && StrUtil.isNotBlank(childTableName)) {
            String childPkg = StrUtil.isNotBlank(childPackageName) ? childPackageName : pkg;
            String childClassName = toCamelCase(childTableName, true);
            String childPageName = uncapitalize(childClassName);
            String childFrontendModule = getFrontendModule(childPkg);
            String childModuleDirName = resolveModuleDirName(bizDir, childPkg, moduleName);
            String childJavaAbsRoot = normalizePath(projectRoot.getAbsolutePath())
                    + "/" + getBizDirName() + "/" + childModuleDirName + "/src/main/java/cn/jfspeed/" + childPkg;
            String childJavaRelRoot = backendRepoName + "/" + getBizDirName() + "/" + childModuleDirName
                    + "/src/main/java/cn/jfspeed/" + childPkg;

            if (code.childEntity() != null) {
                entries.add(new FileEntry("childEntity",
                        childJavaRelRoot + "/entity/" + childClassName + ".java",
                        childJavaAbsRoot + "/entity/" + childClassName + ".java", code.childEntity()));
            }
            if (code.childMapper() != null) {
                entries.add(new FileEntry("childMapper",
                        childJavaRelRoot + "/mapper/" + childClassName + "Mapper.java",
                        childJavaAbsRoot + "/mapper/" + childClassName + "Mapper.java", code.childMapper()));
            }
            if (code.childService() != null) {
                entries.add(new FileEntry("childService",
                        childJavaRelRoot + "/service/" + childClassName + "Service.java",
                        childJavaAbsRoot + "/service/" + childClassName + "Service.java", code.childService()));
            }
            if (code.childController() != null) {
                entries.add(new FileEntry("childController",
                        childJavaRelRoot + "/controller/" + childClassName + "Controller.java",
                        childJavaAbsRoot + "/controller/" + childClassName + "Controller.java", code.childController()));
            }
            if (code.childFrontendApi() != null) {
                entries.add(new FileEntry("childFrontendApi",
                        webRelRoot + "/api/" + childFrontendModule + ".js",
                        webAbsRoot + "/api/" + childFrontendModule + ".js",
                        standaloneApiContent(code.childFrontendApi())));
            }
            if (code.childFrontendIndexPage() != null) {
                entries.add(new FileEntry("childFrontendIndexPage",
                        webRelRoot + "/pages/" + childFrontendModule + "/" + childPageName + "/index.vue",
                        webAbsRoot + "/pages/" + childFrontendModule + "/" + childPageName + "/index.vue",
                        code.childFrontendIndexPage()));
            }
            if (code.childFrontendSavePage() != null) {
                entries.add(new FileEntry("childFrontendSavePage",
                        webRelRoot + "/pages/" + childFrontendModule + "/" + childPageName + "/save.vue",
                        webAbsRoot + "/pages/" + childFrontendModule + "/" + childPageName + "/save.vue",
                        code.childFrontendSavePage()));
            }
        }
        return entries;
    }

    private static void addFile(List<FileEntry> entries, boolean include, String type,
                                String relativePath, String absolutePath, String content) {
        if (include) {
            entries.add(new FileEntry(type, relativePath, absolutePath, content));
        }
    }

    /** 完整可用的 API 文件内容：补上 `createCrud` 的 import（旧 zip 直接塞片段，缺 import） */
    static String standaloneApiContent(String apiContent) {
        if (apiContent == null) {
            return null;
        }
        if (apiContent.contains("from '@/api/core/crud'")) {
            return apiContent;
        }
        return "import { createCrud, http } from '@/api/core/crud';\n\n" + apiContent;
    }

    private static String normalizePackageName(String packageName) {
        return StrUtil.isNotBlank(packageName) ? packageName : "demo";
    }

    private static String normalizePath(String path) {
        return path == null ? null : path.replace('\\', '/');
    }
}
