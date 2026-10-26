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
package cn.jfspeed.flow.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.flow.entity.WfCategory;
import cn.jfspeed.flow.mapper.WfCategoryMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 流程分类 Service。
 *
 * @author jffast
 */
@Service
public class WfCategoryService extends BaseService<WfCategoryMapper, WfCategory> {

    /**
     * 分页查询流程分类。
     */
    public PageRes<WfCategory> page(String categoryName, String categoryCode, PageReq pageReq) {
        LambdaQueryWrapper<WfCategory> query = new LambdaQueryWrapper<>();
        query.like(StrUtil.isNotBlank(categoryName), WfCategory::getCategoryName, categoryName);
        query.eq(StrUtil.isNotBlank(categoryCode), WfCategory::getCategoryCode, categoryCode);
        query.orderByAsc(WfCategory::getSort);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), query));
    }

    /**
     * 查询全部启用的分类（供下拉选择）。
     */
    public List<WfCategory> listEnabled() {
        return this.list(new LambdaQueryWrapper<WfCategory>()
                .eq(WfCategory::getStatus, 0)
                .orderByAsc(WfCategory::getSort));
    }
}

