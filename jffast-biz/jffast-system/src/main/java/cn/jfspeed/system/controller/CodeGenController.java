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
package cn.jfspeed.system.controller;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.util.CodeGenerator;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.dto.SaveLocalRequest;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/sys/codegen")
@Tag(name = "开发 - 代码生成器")
public class CodeGenController {

    private static final String PERM_VIEW = "system:codegen:view";
    private static final String PERM_GEN  = "system:codegen:generate";

    @Resource
    private CodeGenerator codeGenerator;

    @Operation(summary = "获取所有表")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/tables")
    public R<List<CodeGenerator.TableInfo>> tables() {
        return R.success(codeGenerator.listTables());
    }

    @Operation(summary = "预览生成代码")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/preview")
    public R<CodeGenerator.GeneratedCode> preview(@RequestParam String tableName,
                                                   @RequestParam(defaultValue = "") String packageName,
                                                   @RequestParam(defaultValue = "jffast") String author,
                                                   @RequestParam(defaultValue = "false") boolean includeDto,
                                                   @RequestParam(defaultValue = "crud") String genType,
                                                   @RequestParam(required = false) String parentField,
                                                   @RequestParam(required = false) String nameField,
                                                   @RequestParam(required = false) String childTableName,
                                                   @RequestParam(required = false) String childForeignKey,
                                                   @RequestParam(required = false) String childPackageName,
                                                   @RequestParam(required = false) String moduleName) {
        return R.success(codeGenerator.generate(tableName, packageName, author, includeDto, genType,
                parentField, nameField, childTableName, childForeignKey, childPackageName));
    }

    @Operation(summary = "获取表的列信息")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/columns")
    public R<List<CodeGenerator.ColumnInfo>> columns(@RequestParam String tableName) {
        return R.success(codeGenerator.getColumns(tableName));
    }

    @Operation(summary = "检查本地是否已存在生成文件")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/check-local")
    public R<Map<String, Object>> checkLocal(@RequestParam String tableName,
                                                @RequestParam(defaultValue = "") String packageName,
                                                @RequestParam(required = false) String moduleName) {
        if (CodeGenerator.isRunningFromJar()) {
            return R.error("JAR运行时无本地文件");
        }
        return R.success(codeGenerator.checkFilesExist(tableName, packageName, moduleName));
    }

    @Operation(summary = "保存代码到本地（仅本地开发环境）")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_GEN)
    @RateLimit(value = "default")
    @PostMapping("/save-local")
    public R<Map<String, Object>> saveLocal(@RequestBody @Validated SaveLocalRequest request) {
        String tableName = request.getTableName();
        String packageName = request.getPackageName() != null ? request.getPackageName() : "";
        String author = request.getAuthor() != null ? request.getAuthor() : "jffast";
        boolean overwrite = Boolean.TRUE.equals(request.getOverwrite());
        boolean includeDto = Boolean.TRUE.equals(request.getIncludeDto());
        List<String> types = request.getTypes();
        String genType = request.getGenType() != null ? request.getGenType() : "crud";
        String parentField = request.getParentField();
        String nameField = request.getNameField();
        String childTableName = request.getChildTableName();
        String childForeignKey = request.getChildForeignKey();
        String childPackageName = request.getChildPackageName();
        String moduleName = request.getModuleName();

        if (CodeGenerator.isRunningFromJar()) {
            return R.error("JAR运行时不支持保存到本地，请改用「打包下载」获取 zip");
        }

        Map<String, String> savedPaths = codeGenerator.saveToLocal(tableName, packageName, author, types,
                overwrite, includeDto, genType, parentField, nameField, childTableName, childForeignKey, childPackageName,
                moduleName);
        if (savedPaths == null) {
            return R.error("保存失败：无法识别本地项目路径");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("message", "代码已保存到本地");
        result.put("paths", savedPaths);
        return R.success(result);
    }

    /**
     * 打包下载生成的代码（zip）。
     * <p>
     * 与 {@code /save-local} 的区别：不写本地磁盘，直接在内存中打包后返回，
     * 因此 JAR / Docker / K8s 部署同样可用——这类环境下没有可写的工程目录，
     * `save-local` 只能拒绝，而生成器的价值恰恰在这类环境里最容易丢失。
     */
    @Operation(summary = "打包下载生成代码（zip，JAR/容器部署下使用）")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_GEN)
    @RateLimit(value = "default")
    @PostMapping("/download-zip")
    public void downloadZip(@RequestBody @Validated SaveLocalRequest request, HttpServletResponse response)
            throws IOException {
        byte[] zip = codeGenerator.buildZip(
                request.getTableName(),
                request.getPackageName() != null ? request.getPackageName() : "",
                request.getAuthor() != null ? request.getAuthor() : "jffast",
                request.getTypes(),
                Boolean.TRUE.equals(request.getIncludeDto()),
                request.getGenType() != null ? request.getGenType() : "crud",
                request.getParentField(),
                request.getNameField(),
                request.getChildTableName(),
                request.getChildForeignKey(),
                request.getChildPackageName(),
                request.getModuleName());

        // RFC 5987：filename* 用 UTF-8 百分号编码，避免中文表名在部分浏览器乱码
        String encoded = URLEncoder.encode(request.getTableName() + "-code.zip", StandardCharsets.UTF_8);
        response.reset();
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"code.zip\"; filename*=UTF-8''" + encoded);
        response.setContentLength(zip.length);
        try (OutputStream out = response.getOutputStream()) {
            out.write(zip);
        }
    }

    @Operation(summary = "获取代码生成环境信息")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/env")
    public R<Map<String, Object>> env() {
        Map<String, Object> info = new HashMap<>();
        boolean isLocal = !CodeGenerator.isRunningFromJar();
        info.put("isLocal", isLocal);
        info.put("mode", isLocal ? "本地开发模式" : "JAR运行模式");
        info.put("projectType", CodeGenerator.detectProjectType());
        // 模块命名前缀与业务聚合目录名由后端统一下发，避免前端硬编码导致前后端漂移
        info.put("namingPrefix", CodeGenerator.getNamingPrefix());
        info.put("bizDirName", CodeGenerator.getBizDirName());
        if (isLocal) {
            info.put("projectRoot", CodeGenerator.getProjectRoot());
            info.put("frontendRoot", CodeGenerator.getFrontendProjectRoot());
        }
        info.put("tip", isLocal
                ? "当前为本地开发环境，生成代码后可点击「保存到本地」按钮，代码将自动写入对应模块目录"
                : "当前为 JAR 运行模式，不支持直接保存到本地，请使用「下载文件」功能");
        return R.success(info);
    }

    @Operation(summary = "获取当前项目的业务模块列表")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_VIEW)
    @GetMapping("/modules")
    public R<List<String>> modules() {
        if (CodeGenerator.isRunningFromJar()) {
            return R.error("JAR运行时无本地文件");
        }
        return R.success(codeGenerator.listModules());
    }

    @Operation(summary = "创建新业务模块")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = PERM_GEN)
    @RateLimit(value = "default")
    @PostMapping("/create-module")
    public R<Map<String, Object>> createModule(@RequestParam String moduleName) {
        if (CodeGenerator.isRunningFromJar()) {
            return R.error("JAR运行时不支持创建模块");
        }
        Map<String, Object> result = codeGenerator.createModule(moduleName);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return R.success(result);
        }
        return R.error((String) result.get("message"));
    }
}
