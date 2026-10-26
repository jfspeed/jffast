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
import cn.jfspeed.ai.entity.AiDocument;
import cn.jfspeed.ai.mapper.AiDocumentMapper;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import org.springframework.stereotype.Service;

/**
 * AI 文档 服务实现
 *
 * @author jffast
 */
@Service
public class AiDocumentService extends BaseService<AiDocumentMapper, AiDocument> {

    /**
     * 分页查询文档
     */
    public PageRes<AiDocument> page(Integer pageNo, Integer pageSize, Long kbId, String title) {
        LambdaQueryWrapper<AiDocument> query = new LambdaQueryWrapper<>();
        query.eq(kbId != null, AiDocument::getKbId, kbId);
        query.like(StrUtil.isNotBlank(title), AiDocument::getTitle, title);
        query.orderByDesc(AiDocument::getCreateTime);
        Page<AiDocument> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 获取文档详情
     */
    public AiDocument getDocDetail(Long id) {
        return this.getById(id);
    }
}