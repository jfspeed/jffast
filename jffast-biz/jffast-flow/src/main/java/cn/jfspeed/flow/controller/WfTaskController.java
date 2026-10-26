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
package cn.jfspeed.flow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.flow.domain.dto.WfCompleteTaskRequest;
import cn.jfspeed.flow.domain.vo.WfHistoricTaskVO;
import cn.jfspeed.flow.domain.vo.WfTaskVO;
import cn.jfspeed.flow.service.FlowAccessService;
import cn.jfspeed.flowable.service.FlowService;
import cn.jfspeed.log.annotation.SLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.flowable.task.api.Task;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务管理 Controller（待办/签收/审批/转办）。
 *
 * @author jffast
 */
@RestController
@RequestMapping("/flow/task")
@Tag(name = "任务管理")
public class WfTaskController {

    private final FlowService flowService;
    private final FlowAccessService flowAccessService;

    public WfTaskController(FlowService flowService, FlowAccessService flowAccessService) {
        this.flowService = flowService;
        this.flowAccessService = flowAccessService;
    }

    @GetMapping("/todo/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:view")
    @Operation(summary = "我的待办任务（分页）")
    public R todoPage(@RequestParam(defaultValue = "1") Integer pageNo,
                      @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = flowAccessService.currentUserId();
        long total = flowService.countTodoTasks(userId);
        List<WfTaskVO> list = toTaskVOs(flowService.listTodoTasks(userId, (pageNo - 1) * pageSize, pageSize));
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @GetMapping("/candidate/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:view")
    @Operation(summary = "我的候选任务（任务池，未签收）")
    public R candidatePage(@RequestParam(defaultValue = "1") Integer pageNo,
                           @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = flowAccessService.currentUserId();
        long total = flowService.countCandidateTasks(userId);
        List<WfTaskVO> list = toTaskVOs(flowService.listCandidateTasks(userId, (pageNo - 1) * pageSize, pageSize));
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @SLog(title = "签收任务")
    @PostMapping("/claim/{taskId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:claim")
    @Operation(summary = "签收任务（任务池模式：从候选变为指定办理人）")
    public R claim(@PathVariable("taskId") String taskId) {
        String userId = flowAccessService.currentUserId();
        Task task = requireTask(taskId);
        flowAccessService.checkClaimable(task, userId);
        flowService.claimTask(taskId, userId);
        return R.success();
    }

    @SLog(title = "审批任务")
    @PostMapping("/complete")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:complete")
    @Operation(summary = "完成任务（审批）")
    public R complete(@RequestBody @Valid WfCompleteTaskRequest request) {
        String userId = flowAccessService.currentUserId();
        Task task = requireTask(request.getTaskId());
        flowAccessService.checkOperable(task, userId);
        flowService.completeTask(request.getTaskId(), userId, request.getComment(), request.getVariables());
        return R.success();
    }

    @SLog(title = "转办任务")
    @PostMapping("/delegate/{taskId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:delegate")
    @Operation(summary = "转办任务")
    public R delegate(@PathVariable("taskId") String taskId,
                      @RequestParam("toUserId") String toUserId) {
        if (StrUtil.isBlank(toUserId)) {
            throw new BusinessException("转办目标用户不能为空");
        }
        Task task = requireTask(taskId);
        flowAccessService.checkDelegable(task, flowAccessService.currentUserId());
        flowService.delegateTask(taskId, toUserId);
        return R.success();
    }

    @GetMapping("/detail/{taskId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:view")
    @Operation(summary = "任务详情")
    public R detail(@PathVariable("taskId") String taskId) {
        Task task = flowService.getTask(taskId);
        if (task == null) {
            return R.success(null);
        }
        flowAccessService.checkTaskReadable(task, flowAccessService.currentUserId());
        return R.success(WfTaskVO.from(task, flowService.getProcessDefinitionName(task.getProcessDefinitionId())));
    }

    @GetMapping("/history/{processInstanceId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:view")
    @Operation(summary = "任务审批历史（按流程实例）")
    public R taskHistory(@PathVariable("processInstanceId") String processInstanceId) {
        flowAccessService.checkProcessReadable(processInstanceId, flowAccessService.currentUserId());
        // 同 WfHistoryController：历史任务必须映射为 VO，不能直接把 Flowable 实体交给 Jackson
        List<WfHistoricTaskVO> list = flowService.listProcessHistory(processInstanceId)
                .stream().map(WfHistoricTaskVO::from).toList();
        return R.success(list);
    }

    private Task requireTask(String taskId) {
        Task task = flowService.getTask(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在或已完成");
        }
        return task;
    }

    /**
     * 任务实体 → 对外 VO（按流程定义 ID 补查流程名称，同一页内去重查询）。
     */
    private List<WfTaskVO> toTaskVOs(List<Task> tasks) {
        Map<String, String> nameCache = new HashMap<>();
        List<WfTaskVO> list = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            String processDefinitionId = task.getProcessDefinitionId();
            String processDefinitionName = processDefinitionId == null ? null
                    : nameCache.computeIfAbsent(processDefinitionId, flowService::getProcessDefinitionName);
            list.add(WfTaskVO.from(task, processDefinitionName));
        }
        return list;
    }
}
