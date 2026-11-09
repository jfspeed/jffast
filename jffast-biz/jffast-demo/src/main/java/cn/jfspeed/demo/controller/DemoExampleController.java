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
import cn.jfspeed.demo.service.DemoImageSignService;
import cn.jfspeed.common.file.FileRefs;
import cn.jfspeed.common.file.FileUploadResult;
import cn.jfspeed.common.file.FileUploadVO;
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

    @Autowired
    private DemoImageSignService demoImageSignService;

    @GetMapping("/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:view")
    @Operation(summary = "分页查询")
    public R page(DemoExampleQueryRequest query) {
        cn.jfspeed.common.core.util.page.PageRes<DemoExample> pageRes = service.page(query);
        fillImageUrls(pageRes.getList());
        return R.success(pageRes);
    }

    @GetMapping("/detail/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:view")
    @Operation(summary = "详情")
    public R detail(@PathVariable("id") Long id) {
        DemoExample detail = service.detail(id);
        if (detail != null) {
            fillImageUrls(java.util.List.of(detail));
        }
        return R.success(detail);
    }

    @SLog(title = "新增示例")
    @PostMapping("/create")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:add")
    @Operation(summary = "新增")
    public R create(@RequestBody @Valid DemoExampleRequest req) {
        service.saveWithItems(toEntity(req), req.getItems());
        return R.success();
    }

    @SLog(title = "修改示例")
    @PutMapping("/update")
    @DistributedLock(key = "'demo:example:' + #req.id", expireTime = 10000)
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:update")
    @Operation(summary = "修改")
    public R update(@RequestBody @Valid DemoExampleRequest req) {
        service.updateWithItems(toEntity(req), req.getItems());
        return R.success();
    }

    @SLog(title = "删除示例")
    @DeleteMapping("/{id}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:delete")
    @Operation(summary = "删除")
    public R delete(@PathVariable("id") Long id) {
        service.removeWithItems(id);
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
    public R<FileUploadVO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        FileUploadResult result = fileStorageService.uploadFileWithRecord("demo", fileName, file, true);
        FileUploadVO vo = new FileUploadVO();
        Long fileId = result.getFileId();
        vo.setFileId(fileId == null ? null : String.valueOf(fileId));
        vo.setFileRef(FileRefs.of(fileId));
        vo.setFileName(fileName);
        vo.setSize(file.getSize());
        vo.setMime(file.getContentType());
        if (fileId != null) {
            vo.setUrl(demoImageSignService.sign(vo.getFileRef()));
        }
        return R.success(vo);
    }

    /**
     * 请求 → 实体：图片字段归一成**稳定引用** {@code file:{id}}。
     *
     * <p>前端回传的是带签名的直显地址（出参形态），直接落库会存下一条会过期的签名 URL；
     * 这里统一还原成稳定引用，签名由读取时换发。
     */
    private DemoExample toEntity(DemoExampleRequest req) {
        DemoExample entity = BeanUtil.copyProperties(req, DemoExample.class);
        if (entity.getImage() != null) {
            entity.setImage(FileRefs.toStableRef(entity.getImage()));
        }
        return entity;
    }

    /**
     * 图片换发：出参 {@code image} 换成短期签名 URL，同时把稳定引用放进 {@code imageRef}。
     *
     * <p>前端展示用 image，提交用 imageRef——否则库里会存下会过期的签名 URL。
     */
    private void fillImageUrls(List<DemoExample> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (DemoExample item : list) {
            if (item == null) {
                continue;
            }
            String ref = item.getImage();
            String signed = demoImageSignService.sign(ref);
            if (signed != null) {
                item.setImageRef(ref);
                item.setImage(signed);
            }
        }
    }

    @PutMapping("/updateStatus")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "demo:example:update")
    @Operation(summary = "修改状态（仅更新状态列，避免覆盖其它字段）")
    public R updateStatus(@RequestParam("id") Long id, @RequestParam("status") Integer status) {
        service.updateStatus(id, status);
        return R.success();
    }
}