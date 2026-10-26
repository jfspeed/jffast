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
package cn.jfspeed.demo.service;

import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.demo.domain.dto.DemoExampleExcel;
import cn.jfspeed.demo.domain.dto.DemoExampleQueryRequest;
import cn.jfspeed.demo.entity.DemoExample;
import cn.jfspeed.demo.mapper.DemoExampleMapper;
import org.springframework.stereotype.Service;

/**
 * @author jffast
 */
@Service
public class DemoExampleService extends BaseService<DemoExampleMapper, DemoExample> {

    public PageRes<DemoExample> page(DemoExampleQueryRequest query) {
        LambdaQueryWrapper<DemoExample> wrapper = buildQuery(query);
        Page<DemoExample> page = this.page(query.getPage(), wrapper);
        return PageUtil.resultPageMp(page);
    }

    public List<DemoExample> listByQuery(DemoExampleQueryRequest query) {
        return this.list(buildQuery(query));
    }

    private LambdaQueryWrapper<DemoExample> buildQuery(DemoExampleQueryRequest query) {
        LambdaQueryWrapper<DemoExample> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getTitle()), DemoExample::getTitle, query.getTitle());
        wrapper.eq(query.getStatus() != null, DemoExample::getStatus, query.getStatus());
        wrapper.eq(StrUtil.isNotBlank(query.getType()), DemoExample::getType, query.getType());
        wrapper.eq(query.getIsRecommend() != null, DemoExample::getIsRecommend, query.getIsRecommend());
        wrapper.orderByDesc(DemoExample::getCreateTime);
        return wrapper;
    }

    public void importDemo(List<DemoExampleExcel> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<DemoExample> entities = list.stream().map(excel -> {
            DemoExample entity = new DemoExample();
            BeanUtil.copyProperties(excel, entity);
            entity.setIsDeleted(0);
            return entity;
        }).collect(Collectors.toList());
        this.saveBatch(entities);
    }

    public boolean updateStatus(Long id, Integer status) {
        return this.lambdaUpdate()
                .eq(DemoExample::getId, id)
                .set(DemoExample::getStatus, status)
                .update();
    }
}