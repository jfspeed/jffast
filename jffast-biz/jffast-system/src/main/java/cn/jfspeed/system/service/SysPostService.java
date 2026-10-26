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
package cn.jfspeed.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.system.domain.dto.SysPostQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysPost;
import cn.jfspeed.system.mapper.SysPostMapper;
import org.springframework.stereotype.Service;

/**
 * 岗位 服务实现
 *
 * @author jffast
 */
@Service
public class SysPostService extends BaseService<SysPostMapper, SysPost> {

    /**
     * 岗位分页列表
     */
    public PageRes<SysPost> page(SysPostQueryRequest query) {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getPostCode()), SysPost::getPostCode, query.getPostCode());
        queryWrapper.like(StrUtil.isNotBlank(query.getPostName()), SysPost::getPostName, query.getPostName());
        queryWrapper.eq(query.getState() != null, SysPost::getState, query.getState());
        queryWrapper.orderByAsc(SysPost::getSort);
        Page<SysPost> page = this.page(query.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 判断岗位编码是否已存在
     */
    public boolean existsByCode(String postCode, Long id) {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SysPost::getPostCode, postCode);
        queryWrapper.ne(id != null, SysPost::getId, id);
        long count = baseMapper.selectCount(queryWrapper);
        return count > 0;
    }
}
