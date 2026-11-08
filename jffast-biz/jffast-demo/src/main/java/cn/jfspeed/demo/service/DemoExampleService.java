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
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.demo.domain.dto.DemoExampleExcel;
import cn.jfspeed.demo.domain.dto.DemoExampleItemRequest;
import cn.jfspeed.demo.domain.dto.DemoExampleQueryRequest;
import cn.jfspeed.demo.entity.DemoExample;
import cn.jfspeed.demo.entity.DemoExampleItem;
import cn.jfspeed.demo.mapper.DemoExampleItemMapper;
import cn.jfspeed.demo.mapper.DemoExampleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author jffast
 */
@Service
public class DemoExampleService extends BaseService<DemoExampleMapper, DemoExample> {

    @Autowired
    private DemoExampleItemMapper demoExampleItemMapper;

    /** 详情：主表 + 明细子行，编辑回显必须走这里，否则生成页的 items 会被空数组覆盖 */
    public DemoExample detail(Long id) {
        DemoExample entity = this.getById(id);
        if (entity != null) {
            entity.setItems(listItems(id));
        }
        return entity;
    }

    public List<DemoExampleItem> listItems(Long exampleId) {
        return demoExampleItemMapper.selectList(new LambdaQueryWrapper<DemoExampleItem>()
                .eq(DemoExampleItem::getExampleId, exampleId)
                .orderByAsc(DemoExampleItem::getSort)
                .orderByAsc(DemoExampleItem::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveWithItems(DemoExample entity, List<DemoExampleItemRequest> items) {
        boolean saved = this.save(entity);
        replaceItems(entity.getId(), items);
        return saved;
    }

    /**
     * 主表更新与明细全量替换在同一事务内：明细非 null 时先删旧行再插入本次提交的行。
     * 主表带 version 时由 @Version 拦截并发；影响行数为 0 视为版本冲突，整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateWithItems(DemoExample entity, List<DemoExampleItemRequest> items) {
        boolean updated = this.updateById(entity);
        if (!updated) {
            throw new BusinessException("数据已被他人修改，请刷新后重试");
        }
        replaceItems(entity.getId(), items);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeWithItems(Long id) {
        replaceItems(id, List.of());
        return this.removeById(id);
    }

    private void replaceItems(Long exampleId, List<DemoExampleItemRequest> items) {
        // null 表示本次请求不涉及明细（部分更新）；空数组才是「清空」
        if (items == null) {
            return;
        }
        demoExampleItemMapper.delete(new LambdaQueryWrapper<DemoExampleItem>()
                .eq(DemoExampleItem::getExampleId, exampleId));
        for (int i = 0; i < items.size(); i++) {
            DemoExampleItem item = BeanUtil.copyProperties(items.get(i), DemoExampleItem.class);
            item.setId(null);
            item.setExampleId(exampleId);
            if (item.getSort() == null) {
                item.setSort(i);
            }
            demoExampleItemMapper.insert(item);
        }
    }

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