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
package cn.jfspeed.demo.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.demo.domain.dto.DemoExampleExcel;
import cn.jfspeed.demo.domain.dto.DemoExampleQueryRequest;
import cn.jfspeed.demo.domain.dto.DemoExampleRequest;
import cn.jfspeed.demo.entity.DemoExample;
import cn.jfspeed.demo.service.DemoExampleService;
import cn.jfspeed.excel.util.ExcelUtil;
import cn.jfspeed.file.service.FileStorageService;
import cn.jfspeed.log.annotation.SLog;
import org.springframework.beans.factory.annotation.Autowired;
import cn.jfspeed.security.annotation.DistributedLock;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author jffast
 */
@RestController
@RequestMapping("/demo/example")
@Tag(name = "示例管理")
public class DemoExampleController extends BaseController<DemoExampleService, DemoExample> {

    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:view")
    @Operation(summary = "分页查询")
    public R page(DemoExampleQueryRequest query) {
        return R.success(service.page(query));
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:view")
    @Operation(summary = "详情")
    public R detail(@PathVariable("id") Long id) {
        return R.success(service.getById(id));
    }

    @SLog(title = "新增示例")
    @PostMapping("/create")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:add")
    @Operation(summary = "新增")
    public R create(@RequestBody @Valid DemoExampleRequest req) {
        service.save(BeanUtil.copyProperties(req, DemoExample.class));
        return R.success();
    }

    @SLog(title = "修改示例")
    @PutMapping("/update")
    @DistributedLock(key = "'demo:example:' + #req.id", expireTime = 10000)
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:update")
    @Operation(summary = "修改")
    public R update(@RequestBody @Valid DemoExampleRequest req) {
        service.updateById(BeanUtil.copyProperties(req, DemoExample.class));
        return R.success();
    }

    @SLog(title = "删除示例")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:delete")
    @Operation(summary = "删除")
    public R delete(@PathVariable("id") Long id) {
        service.removeById(id);
        return R.success();
    }

    @GetMapping("/export")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:export")
    @Operation(summary = "导出Excel")
    public void export(HttpServletResponse response, DemoExampleQueryRequest query) {
        List<DemoExample> list = service.listByQuery(query);
        List<DemoExampleExcel> excelList = list.stream()
                .map(e -> BeanUtil.copyProperties(e, DemoExampleExcel.class))
                .collect(Collectors.toList());
        ExcelUtil.export(response, "示例数据.xlsx", excelList, DemoExampleExcel.class);
    }

    @GetMapping("/import-template")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:import")
    @Operation(summary = "导入模板下载")
    public void importTemplate(HttpServletResponse response) {
        ExcelUtil.export(response, "示例导入模板.xlsx", List.of(), DemoExampleExcel.class);
    }

    @SLog(title = "导入示例")
    @PostMapping("/import")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:import")
    @Operation(summary = "导入Excel")
    public R importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        List<DemoExampleExcel> list = ExcelUtil.read(file.getInputStream(), DemoExampleExcel.class);
        service.importDemo(list);
        return R.success();
    }

    @PostMapping("/upload")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:update")
    @Operation(summary = "上传图片")
    public R upload(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String imgUrl = fileStorageService.uploadImgFile("demo", fileName, file);
        return R.success(imgUrl);
    }

    @PutMapping("/updateStatus")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:update")
    @Operation(summary = "修改状态（仅更新状态列，避免覆盖其它字段）")
    public R updateStatus(@RequestParam("id") Long id, @RequestParam("status") Integer status) {
        service.updateStatus(id, status);
        return R.success();
    }
}