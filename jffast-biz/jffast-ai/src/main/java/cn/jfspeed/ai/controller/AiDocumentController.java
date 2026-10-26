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
package cn.jfspeed.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import cn.jfspeed.ai.domain.dto.AiDocumentRequest;
import cn.jfspeed.ai.entity.AiDocument;
import cn.jfspeed.ai.service.AiDocumentService;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * AI 文档管理
 *
 * @author jffast
 */
@Tag(name = "AI 文档管理")
@RestController
@RequestMapping("/ai/document")
public class AiDocumentController extends BaseController<AiDocumentService, AiDocument> {

    @Resource
    private AiDocumentService documentService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:document:view")
    @Operation(summary = "分页查询文档")
    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") Integer pageNo,
                     @RequestParam(defaultValue = "10") Integer pageSize,
                     @RequestParam(required = false) Long kbId,
                     @RequestParam(required = false) String title) {
        return R.success(documentService.page(pageNo, pageSize, kbId, title));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:document:view")
    @Operation(summary = "获取文档详情")
    @GetMapping("/detail/{id}")
    public R<AiDocument> detail(@PathVariable Long id) {
        return R.success(documentService.getDocDetail(id));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:document:add")
    @RateLimit(value = "default")
    @SLog(title = "新增文档")
    @Operation(summary = "新增文档")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid AiDocumentRequest req) {
        AiDocument entity = BeanUtil.copyProperties(req, AiDocument.class);
        entity.setStatus(0); // 待处理
        documentService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:document:edit")
    @RateLimit(value = "default")
    @SLog(title = "修改文档")
    @Operation(summary = "修改文档")
    @PutMapping("/update")
    public R<Void> update(@RequestBody @Valid AiDocumentRequest req) {
        AiDocument entity = BeanUtil.copyProperties(req, AiDocument.class);
        documentService.updateById(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:document:delete")
    @SLog(title = "删除文档")
    @Operation(summary = "删除文档")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        documentService.removeById(id);
        return R.success();
    }
}