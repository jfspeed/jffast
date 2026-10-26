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

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.demo.entity.DemoTenantExample;
import cn.jfspeed.demo.mapper.DemoTenantExampleMapper;
import org.springframework.stereotype.Service;

/**
 * @author jffast
 */
@Service
public class DemoTenantExampleService extends BaseService<DemoTenantExampleMapper, DemoTenantExample> {

    public PageRes<DemoTenantExample> page(Integer pageNo, Integer pageSize, String title, Integer status) {
        LambdaQueryWrapper<DemoTenantExample> query = new LambdaQueryWrapper<>();
        query.like(StrUtil.isNotBlank(title), DemoTenantExample::getTitle, title);
        query.eq(status != null, DemoTenantExample::getStatus, status);
        query.orderByDesc(DemoTenantExample::getCreateTime);
        Page<DemoTenantExample> page = this.page(new Page<>(pageNo, pageSize), query);
        return PageUtil.resultPageMp(page);
    }
}
