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
import cn.jfspeed.ai.domain.dto.AiConversationRequest;
import cn.jfspeed.ai.entity.AiConversation;
import cn.jfspeed.ai.entity.AiMessage;
import cn.jfspeed.ai.service.AiConversationService;
import cn.jfspeed.ai.service.AiMessageService;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseController;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 对话管理
 *
 * @author jffast
 */
@Tag(name = "AI 对话管理")
@RestController
@RequestMapping("/ai/conversation")
public class AiConversationController extends BaseController<AiConversationService, AiConversation> {

    @Resource
    private AiConversationService conversationService;

    @Resource
    private AiMessageService messageService;

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:conversation:view")
    @Operation(summary = "分页查询对话")
    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") Integer pageNo,
                     @RequestParam(defaultValue = "10") Integer pageSize,
                     @RequestParam(required = false) Long kbId,
                     @RequestParam(required = false) String title) {
        return R.success(conversationService.page(pageNo, pageSize, kbId, title));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:conversation:view")
    @Operation(summary = "获取对话详情")
    @GetMapping("/detail/{id}")
    public R<AiConversation> detail(@PathVariable Long id) {
        return R.success(conversationService.getConvDetail(id));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:conversation:view")
    @Operation(summary = "获取对话消息列表")
    @GetMapping("/messages/{conversationId}")
    public R<List<AiMessage>> messages(@PathVariable Long conversationId) {
        conversationService.getOwnedConversation(conversationId);
        return R.success(messageService.listByConversationId(conversationId));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:conversation:add")
    @RateLimit(value = "default")
    @SLog(title = "新增对话")
    @Operation(summary = "新增对话")
    @PostMapping("/create")
    public R<Void> create(@RequestBody @Valid AiConversationRequest req) {
        if (req.getId() != null) {
            throw new BusinessException("新增对话不允许指定 ID");
        }
        AiConversation entity = BeanUtil.copyProperties(req, AiConversation.class);
        // 归属由服务端写入，避免客户端伪造/遗漏 userId 产生无主会话
        entity.setUserId(LoginUtil.getLoginUserId());
        conversationService.save(entity);
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "ai:conversation:delete")
    @SLog(title = "删除对话")
    @Operation(summary = "删除对话")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        conversationService.deleteOwned(id);
        return R.success();
    }
}