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
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.flow.domain.vo.WfHistoricProcessInstanceVO;
import cn.jfspeed.flow.domain.vo.WfHistoricTaskVO;
import cn.jfspeed.flow.service.FlowAccessService;
import cn.jfspeed.flowable.service.FlowService;
import org.flowable.engine.history.HistoricProcessInstance;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史查询 Controller（已办/历史实例/审批记录）。
 *
 * @author jffast
 */
@RestController
@RequestMapping("/flow/history")
@Tag(name = "流程历史")
public class WfHistoryController {

    private final FlowService flowService;
    private final FlowAccessService flowAccessService;

    public WfHistoryController(FlowService flowService, FlowAccessService flowAccessService) {
        this.flowService = flowService;
        this.flowAccessService = flowAccessService;
    }

    @GetMapping("/done/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:task:view")
    @Operation(summary = "我的已办任务（分页）")
    public R donePage(@RequestParam(defaultValue = "1") Integer pageNo,
                      @RequestParam(defaultValue = "10") Integer pageSize) {
        String userId = String.valueOf(LoginUtil.getLoginUserId());
        long total = flowService.countDoneTasks(userId);
        // 必须映射为 VO：Flowable 历史实体带着 revision / originalPersistentState 等内部结构，
        // 直接序列化会让前端拿不到扁平字段（见 WfHistoricTaskVO 的 javadoc）；
        // 历史任务实体不含流程名称，按流程定义 ID 去重补查。
        Map<String, String> nameCache = new HashMap<>(4);
        List<WfHistoricTaskVO> list = flowService.listDoneTasks(userId, (pageNo - 1) * pageSize, pageSize)
                .stream().map(task -> {
                    String processDefinitionId = task.getProcessDefinitionId();
                    String processDefinitionName = processDefinitionId == null ? null
                            : nameCache.computeIfAbsent(processDefinitionId, flowService::getProcessDefinitionName);
                    return WfHistoricTaskVO.from(task, processDefinitionName);
                }).toList();
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @GetMapping("/instance/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "历史流程实例分页查询")
    public R instancePage(@RequestParam(required = false) String processKey,
                          @RequestParam(required = false) String initiatorUserId,
                          @RequestParam(defaultValue = "1") Integer pageNo,
                          @RequestParam(defaultValue = "10") Integer pageSize) {
        // 非超管只能看本人发起的实例，忽略前端传入的任意发起人
        if (!flowAccessService.isSuperAdmin()) {
            initiatorUserId = flowAccessService.currentUserId();
        }
        long total = flowService.countHistoricProcessInstances(processKey, initiatorUserId);
        // 这里原先返回的是引擎内部结构（revision / originalPersistentState），必须映射为 VO
        List<WfHistoricProcessInstanceVO> list = flowService.listHistoricProcessInstances(processKey, initiatorUserId,
                        (pageNo - 1) * pageSize, pageSize)
                .stream().map(WfHistoricProcessInstanceVO::from).toList();
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @GetMapping("/instance/{processInstanceId}/tasks")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "流程实例审批历史（任务列表）")
    public R instanceHistory(@PathVariable("processInstanceId") String processInstanceId) {
        flowAccessService.checkProcessReadable(processInstanceId, flowAccessService.currentUserId());
        // 审批历史抽屉用的就是这份数据，同样必须映射为 VO
        List<WfHistoricTaskVO> list = flowService.listProcessHistory(processInstanceId)
                .stream().map(WfHistoricTaskVO::from).toList();
        return R.success(list);
    }

    @GetMapping("/instance/{processInstanceId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "历史流程实例详情")
    public R instanceDetail(@PathVariable("processInstanceId") String processInstanceId) {
        HistoricProcessInstance instance = flowService.getHistoricProcessInstance(processInstanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        flowAccessService.checkProcessReadable(processInstanceId, flowAccessService.currentUserId());
        return R.success(WfHistoricProcessInstanceVO.from(instance));
    }
}
