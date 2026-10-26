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
import cn.jfspeed.system.domain.dto.SysTaskQueryRequest;
import cn.jfspeed.common.data.base.BaseService;
import cn.jfspeed.common.core.util.page.PageRes;
import cn.jfspeed.common.data.util.page.PageUtil;
import cn.jfspeed.system.entity.SysTask;
import cn.jfspeed.system.mapper.SysTaskMapper;
import org.springframework.stereotype.Service;

/**
 * 异步任务 服务实现
 *
 * @author jffast
 */
@Service
public class SysTaskService extends BaseService<SysTaskMapper, SysTask> {

    /**
     * 异步任务分页列表
     */
    public PageRes<SysTask> page(SysTaskQueryRequest query) {
        return page(query, null);
    }

    /**
     * 异步任务分页列表（ownerId 非空时仅返回该用户创建的任务）
     */
    public PageRes<SysTask> page(SysTaskQueryRequest query, Long ownerId) {
        LambdaQueryWrapper<SysTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(query.getTitle()), SysTask::getTitle, query.getTitle());
        queryWrapper.eq(StrUtil.isNotBlank(query.getTaskType()), SysTask::getTaskType, query.getTaskType());
        queryWrapper.eq(StrUtil.isNotBlank(query.getBizType()), SysTask::getBizType, query.getBizType());
        queryWrapper.eq(query.getStatus() != null, SysTask::getStatus, query.getStatus());
        queryWrapper.eq(ownerId != null, SysTask::getCreateBy, ownerId);
        queryWrapper.orderByDesc(SysTask::getCreateTime);
        Page<SysTask> page = this.page(query.getPage(), queryWrapper);
        return PageUtil.resultPageMp(page);
    }

    /**
     * 创建任务（待处理）
     */
    public SysTask createTask(String taskType, String bizType, String title) {
        SysTask task = new SysTask();
        task.setTaskType(taskType);
        task.setBizType(bizType);
        task.setTitle(title);
        task.setStatus(SysTask.STATUS_PENDING);
        task.setProgress(0);
        this.save(task);
        return task;
    }

    /**
     * 标记任务进行中
     */
    public void markRunning(Long id) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setStatus(SysTask.STATUS_RUNNING);
        this.updateById(task);
    }

    /**
     * 更新进度（0-100）
     */
    public void markProgress(Long id, int progress) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setProgress(progress);
        this.updateById(task);
    }

    /**
     * 记录总行数
     */
    public void markTotal(Long id, int total) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setTotalRows(total);
        this.updateById(task);
    }

    /**
     * 标记任务完成（进度置 100）
     */
    public void markSuccess(Long id, String filePath, String fileName, int total, int success, int fail, String failDetail) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setStatus(SysTask.STATUS_SUCCESS);
        task.setProgress(100);
        task.setFilePath(filePath);
        task.setFileName(fileName);
        task.setTotalRows(total);
        task.setSuccessRows(success);
        task.setFailRows(fail);
        task.setFailDetail(failDetail);
        this.updateById(task);
    }

    /**
     * 标记任务失败
     */
    public void markFail(Long id, String errorMsg) {
        SysTask task = new SysTask();
        task.setId(id);
        task.setStatus(SysTask.STATUS_FAIL);
        task.setErrorMsg(StrUtil.maxLength(errorMsg, 2000));
        this.updateById(task);
    }
}
