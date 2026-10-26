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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.ai.entity.AiKnowledgeBase;
import cn.jfspeed.ai.mapper.AiKnowledgeBaseMapper;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import org.springframework.stereotype.Service;

/**
 * AI 知识库 服务实现
 *
 * @author jffast
 */
@Service
public class AiKnowledgeBaseService extends BaseService<AiKnowledgeBaseMapper, AiKnowledgeBase> {

    /**
     * 分页查询知识库
     */
    public PageRes<AiKnowledgeBase> page(Integer pageNo, Integer pageSize, String name, String code) {
        LambdaQueryWrapper<AiKnowledgeBase> query = new LambdaQueryWrapper<>();
        query.like(StrUtil.isNotBlank(name), AiKnowledgeBase::getName, name);
        query.like(StrUtil.isNotBlank(code), AiKnowledgeBase::getCode, code);
        query.orderByDesc(AiKnowledgeBase::getCreateTime);
        Page<AiKnowledgeBase> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 检查编码是否已存在
     */
    public boolean hasCode(String code, Long id) {
        LambdaQueryWrapper<AiKnowledgeBase> query = new LambdaQueryWrapper<>();
        query.eq(AiKnowledgeBase::getCode, code);
        query.ne(id != null, AiKnowledgeBase::getId, id);
        return this.count(query) > 0;
    }
}