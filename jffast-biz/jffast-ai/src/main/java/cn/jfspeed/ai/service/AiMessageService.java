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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.jfspeed.ai.entity.AiMessage;
import cn.jfspeed.ai.mapper.AiMessageMapper;
import cn.jfspeed.common.data.base.BaseService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 消息 服务实现
 *
 * @author jffast
 */
@Service
public class AiMessageService extends BaseService<AiMessageMapper, AiMessage> {

    /**
     * 获取对话的消息列表
     */
    public List<AiMessage> listByConversationId(Long conversationId) {
        LambdaQueryWrapper<AiMessage> query = new LambdaQueryWrapper<>();
        query.eq(AiMessage::getConversationId, conversationId);
        query.orderByAsc(AiMessage::getCreateTime);
        return this.list(query);
    }
}