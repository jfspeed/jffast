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
import cn.jfspeed.ai.domain.dto.AiKnowledgeBaseRequest;
import cn.jfspeed.ai.entity.AiKnowledgeBase;
import cn.jfspeed.ai.service.AiKnowledgeBaseService;
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
 * AI 知识库管理
 *
 * @author jffast
 */
@Tag(name = "AI 知识库管理")
@RestController
@RequestMapping("/ai/knowledge-base")
public class AiKnowledgeBaseController extends BaseController<AiKnowledgeBaseService, AiKnowledgeBase> {

    @Resource
    private AiKnowledgeBaseService knowledgeBaseService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:knowledge-base:view")
    @Operation(summary = "分页查询知识库")
    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") Integer pageNo,
                     @RequestParam(defaultValue = "10") Integer pageSize,
                     @RequestParam(required = false) String name,
                     @RequestParam(required = false) String code) {
        return R.success(knowledgeBaseService.page(pageNo, pageSize, name, code));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:knowledge-base:view")
    @Operation(summary = "获取知识库详情")
    @GetMapping("/detail/{id}")
    public R<AiKnowledgeBase> detail(@PathVariable Long id) {
        return R.success(knowledgeBaseService.getById(id));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:knowledge-base:add")
    @RateLimit(value = "default")
    @SLog(title = "新增知识库")
    @Operation(summary = "新增知识库")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid AiKnowledgeBaseRequest req) {
        AiKnowledgeBase entity = BeanUtil.copyProperties(req, AiKnowledgeBase.class);
        if (knowledgeBaseService.hasCode(entity.getCode(), null)) {
            return R.error("知识库编码已存在");
        }
        knowledgeBaseService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:knowledge-base:edit")
    @RateLimit(value = "default")
    @SLog(title = "修改知识库")
    @Operation(summary = "修改知识库")
    @PutMapping("/update")
    public R<Void> update(@RequestBody @Valid AiKnowledgeBaseRequest req) {
        AiKnowledgeBase entity = BeanUtil.copyProperties(req, AiKnowledgeBase.class);
        if (knowledgeBaseService.hasCode(entity.getCode(), entity.getId())) {
            return R.error("知识库编码已存在");
        }
        knowledgeBaseService.updateById(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:knowledge-base:delete")
    @SLog(title = "删除知识库")
    @Operation(summary = "删除知识库")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.removeById(id);
        return R.success();
    }
}