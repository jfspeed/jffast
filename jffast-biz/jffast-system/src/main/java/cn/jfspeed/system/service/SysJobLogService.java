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
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageReq;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.domain.dto.SysJobLogQueryRequest;
import cn.jfspeed.system.entity.SysJobLog;
import cn.jfspeed.system.mapper.SysJobLogMapper;
import org.springframework.stereotype.Service;

/**
 * @author jffast
 */
@Service
public class SysJobLogService extends BaseService<SysJobLogMapper, SysJobLog> {

    public PageRes<SysJobLog> page(SysJobLogQueryRequest query, PageReq pageReq) {
        LambdaQueryWrapper<SysJobLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getJobName()), SysJobLog::getJobName, query.getJobName());
        queryWrapper.eq(StrUtil.isNotBlank(query.getJobGroup()), SysJobLog::getJobGroup, query.getJobGroup());
        queryWrapper.eq(query.getStatus() != null, SysJobLog::getStatus, query.getStatus());
        queryWrapper.ge(StrUtil.isNotBlank(query.getStartTime()), SysJobLog::getCreateTime, query.getStartTime());
        queryWrapper.le(StrUtil.isNotBlank(query.getEndTime()), SysJobLog::getCreateTime, query.getEndTime());
        queryWrapper.orderByDesc(SysJobLog::getCreateTime);
        return PageUtil.resultPageMp(baseMapper.selectPage(pageReq.getPage(), queryWrapper));
    }

    public void clean() {
        remove(new LambdaQueryWrapper<>());
    }
}
