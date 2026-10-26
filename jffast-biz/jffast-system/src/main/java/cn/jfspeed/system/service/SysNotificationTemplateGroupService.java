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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysNotificationTemplateGroup;
import cn.jfspeed.system.mapper.SysNotificationTemplateGroupMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知模板分组服务
 *
 * @author jffast
 */
@Service
public class SysNotificationTemplateGroupService extends BaseService<SysNotificationTemplateGroupMapper, SysNotificationTemplateGroup> {

    /**
     * 查询所有启用的分组
     */
    public List<SysNotificationTemplateGroup> listEnabled() {
        return list(new LambdaQueryWrapper<SysNotificationTemplateGroup>()
                .eq(SysNotificationTemplateGroup::getStatus, 1)
                .orderByAsc(SysNotificationTemplateGroup::getSort));
    }

    /**
     * 分页查询分组
     */
    public PageRes<SysNotificationTemplateGroup> page(SysNotificationTemplateGroup param, PageReq pageReq) {
        LambdaQueryWrapper<SysNotificationTemplateGroup> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(param.getName()), SysNotificationTemplateGroup::getName, param.getName());
        queryWrapper.eq(param.getStatus() != null, SysNotificationTemplateGroup::getStatus, param.getStatus());
        queryWrapper.orderByAsc(SysNotificationTemplateGroup::getSort);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }
}
