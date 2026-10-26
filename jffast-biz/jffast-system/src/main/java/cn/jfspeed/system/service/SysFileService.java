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
import cn.jfspeed.system.domain.dto.SysFileQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysFile;
import cn.jfspeed.system.mapper.SysFileMapper;
import org.springframework.stereotype.Service;

/**
 * 文件记录 服务实现
 *
 * @author jffast
 */
@Service
public class SysFileService extends BaseService<SysFileMapper, SysFile> {

    /**
     * 文件分页列表
     */
    public PageRes<SysFile> page(SysFileQueryRequest query) {
        LambdaQueryWrapper<SysFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getFileName()), SysFile::getFileName, query.getFileName());
        queryWrapper.eq(StrUtil.isNotBlank(query.getBizType()), SysFile::getBizType, query.getBizType());
        queryWrapper.eq(StrUtil.isNotBlank(query.getStorageType()), SysFile::getStorageType, query.getStorageType());
        queryWrapper.orderByDesc(SysFile::getCreateTime);
        Page<SysFile> page = this.page(query.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }
}
