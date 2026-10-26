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
import cn.jfspeed.system.entity.SysNotificationTemplate;
import cn.jfspeed.system.mapper.SysNotificationTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知模板服务
 *
 * @author jffast
 */
@Service
public class SysNotificationTemplateService extends BaseService<SysNotificationTemplateMapper, SysNotificationTemplate> {

    /**
     * 分页查询模板
     */
    public PageRes<SysNotificationTemplate> page(SysNotificationTemplate param, PageReq pageReq) {
        LambdaQueryWrapper<SysNotificationTemplate> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(param.getGroupId() != null, SysNotificationTemplate::getGroupId, param.getGroupId());
        queryWrapper.like(StrUtil.isNotBlank(param.getName()), SysNotificationTemplate::getName, param.getName());
        queryWrapper.like(StrUtil.isNotBlank(param.getCode()), SysNotificationTemplate::getCode, param.getCode());
        queryWrapper.eq(StrUtil.isNotBlank(param.getType()), SysNotificationTemplate::getType, param.getType());
        queryWrapper.eq(param.getStatus() != null, SysNotificationTemplate::getStatus, param.getStatus());
        queryWrapper.orderByDesc(SysNotificationTemplate::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }

    /**
     * 根据编码获取模板并渲染
     */
    public Map<String, String> renderByCode(String code, Map<String, Object> params) {
        SysNotificationTemplate template = getOne(new LambdaQueryWrapper<SysNotificationTemplate>()
                .eq(SysNotificationTemplate::getCode, code)
                .eq(SysNotificationTemplate::getStatus, 1));
        if (template == null) {
            return null;
        }

        String title = template.getTitleTemplate();
        String content = template.getContentTemplate();

        // 渲染模板变量
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                title = title.replace(placeholder, value);
                content = content.replace(placeholder, value);
            }
        }

        Map<String, String> result = new HashMap<>();
        result.put("title", title);
        result.put("content", content);
        result.put("type", template.getType());
        result.put("level", template.getLevel());
        result.put("recipientType", template.getRecipientType());
        return result;
    }
}
