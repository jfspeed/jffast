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
package cn.jfspeed.ai.service;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.ai.entity.AiMessage;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.ai.entity.AiConversation;
import cn.jfspeed.ai.mapper.AiConversationMapper;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.common.security.util.LoginUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * AI 对话 服务实现
 *
 * @author jffast
 */
@Service
public class AiConversationService extends BaseService<AiConversationMapper, AiConversation> {

    private final AiMessageService messageService;

    public AiConversationService(AiMessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 分页查询对话
     */
    public PageRes<AiConversation> page(Integer pageNo, Integer pageSize, Long kbId, String title) {
        LambdaQueryWrapper<AiConversation> query = new LambdaQueryWrapper<>();
        query.eq(AiConversation::getUserId, LoginUtil.getLoginUserId());
        query.eq(kbId != null, AiConversation::getKbId, kbId);
        query.like(StrUtil.isNotBlank(title), AiConversation::getTitle, title);
        query.orderByDesc(AiConversation::getCreateTime);
        Page<AiConversation> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 获取对话详情（仅本人或超管可访问）。
     */
    public AiConversation getConvDetail(Long id) {
        return getOwnedConversation(id);
    }

    /**
     * 按归属读取对话：超管可读全部，普通用户只能读自己的。
     */
    public AiConversation getOwnedConversation(Long id) {
        AiConversation conversation = this.getById(id);
        if (conversation == null) {
            throw new BusinessException("对话不存在");
        }
        if (!isSuperAdmin() && !Objects.equals(conversation.getUserId(), LoginUtil.getLoginUserId())) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权访问该对话");
        }
        return conversation;
    }

    /**
     * 删除对话及其消息（仅本人或超管可删）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOwned(Long id) {
        getOwnedConversation(id);
        LambdaQueryWrapper<AiMessage> query = new LambdaQueryWrapper<>();
        query.eq(AiMessage::getConversationId, id);
        messageService.remove(query);
        this.removeById(id);
    }

    private boolean isSuperAdmin() {
        return LoginUtil.getRoleCodeList().contains(BaseConstant.ROLE_SUPER_ADMIN)
                || LoginUtil.getRoleIdList().contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG);
    }
}