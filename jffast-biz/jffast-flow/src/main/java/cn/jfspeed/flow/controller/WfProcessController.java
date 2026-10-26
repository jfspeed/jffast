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
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.flow.domain.dto.WfDeployRequest;
import cn.jfspeed.flow.domain.vo.WfProcessDefinitionVO;
import cn.jfspeed.flow.domain.vo.WfProcessInstanceVO;
import cn.jfspeed.flow.domain.dto.WfStartProcessRequest;
import cn.jfspeed.flow.service.FlowAccessService;
import cn.jfspeed.flowable.service.FlowService;
import org.flowable.engine.history.HistoricProcessInstance;
import cn.jfspeed.log.annotation.SLog;
import cn.jfspeed.security.annotation.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程定义 / 部署 / 实例管理 Controller。
 *
 * @author jffast
 */
@RestController
@RequestMapping("/flow/process")
@Tag(name = "流程管理")
public class WfProcessController {

    private final FlowService flowService;
    private final FlowAccessService flowAccessService;

    public WfProcessController(FlowService flowService, FlowAccessService flowAccessService) {
        this.flowService = flowService;
        this.flowAccessService = flowAccessService;
    }

    // ─── 流程部署 ──────────────────────────────────────

    @SLog(title = "部署流程")
    @PostMapping("/deploy")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:deploy")
    @Operation(summary = "部署 BPMN XML")
    public R deploy(@RequestBody WfDeployRequest request) {
        String resourceName = request.getResourceName() != null ? request.getResourceName() : "process.bpmn20.xml";
        return R.success(flowService.deploy(request.getName(), request.getCategory(), resourceName,
                request.getBpmnXml()).getId());
    }

    @SLog(title = "删除流程部署")
    @DeleteMapping("/deployment/{deploymentId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:deploy")
    @Operation(summary = "删除流程部署（级联删除实例）")
    public R deleteDeployment(@PathVariable("deploymentId") String deploymentId) {
        flowService.deleteDeployment(deploymentId, true);
        return R.success();
    }

    // ─── 流程定义 ──────────────────────────────────────

    @GetMapping("/definition/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "流程定义分页查询（最新版本）")
    public R definitionPage(@RequestParam(required = false) String processKey,
                            @RequestParam(required = false) String category,
                            @RequestParam(defaultValue = "1") Integer pageNo,
                            @RequestParam(defaultValue = "10") Integer pageSize) {
        long total = flowService.countProcessDefinitions(processKey, category);
        // **必须映射为 VO**：Flowable 的 ProcessDefinitionEntityImpl 带惰性属性（identityLinks 等），
        // 直接交给 Jackson 序列化会在响应体写出时抛 NPE → code=500、页面拿不到数据。
        List<WfProcessDefinitionVO> list = flowService.listProcessDefinitions(processKey, category, (pageNo - 1) * pageSize, pageSize)
                .stream().map(WfProcessDefinitionVO::from).toList();
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @GetMapping("/definition/{processDefinitionId}/xml")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "获取流程定义 BPMN XML（设计器回显用）")
    public R getBpmnXml(@PathVariable("processDefinitionId") String processDefinitionId) {
        return R.success(flowService.getBpmnXml(processDefinitionId));
    }

    @GetMapping("/definition/{processDefinitionId}/diagram")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "获取流程图（PNG）")
    public void getDiagram(@PathVariable("processDefinitionId") String processDefinitionId,
                           HttpServletResponse response) throws IOException {
        try (InputStream in = flowService.getProcessDiagram(processDefinitionId);
             OutputStream out = response.getOutputStream()) {
            response.setContentType("image/png");
            if (in != null) {
                in.transferTo(out);
            }
        }
    }

    @SLog(title = "挂起流程定义")
    @PutMapping("/definition/{processDefinitionId}/suspend")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:deploy")
    @Operation(summary = "挂起流程定义")
    public R suspendDefinition(@PathVariable("processDefinitionId") String processDefinitionId) {
        flowService.suspendProcessDefinition(processDefinitionId);
        return R.success();
    }

    @SLog(title = "激活流程定义")
    @PutMapping("/definition/{processDefinitionId}/activate")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:deploy")
    @Operation(summary = "激活流程定义")
    public R activateDefinition(@PathVariable("processDefinitionId") String processDefinitionId) {
        flowService.activateProcessDefinition(processDefinitionId);
        return R.success();
    }

    // ─── 流程实例 ──────────────────────────────────────

    @SLog(title = "发起流程")
    @PostMapping("/instance/start")
    @RateLimit(value = "default")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:start")
    @Operation(summary = "发起流程实例")
    public R startInstance(@RequestBody WfStartProcessRequest request) {
        String initiatorUserId = flowAccessService.currentUserId();
        return R.success(flowService.startProcess(request.getProcessKey(), request.getBusinessKey(),
                initiatorUserId, request.getVariables()).getId());
    }

    @GetMapping("/instance/page")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:view")
    @Operation(summary = "运行中流程实例分页查询")
    public R instancePage(@RequestParam(required = false) String processKey,
                          @RequestParam(required = false) String initiatorUserId,
                          @RequestParam(defaultValue = "1") Integer pageNo,
                          @RequestParam(defaultValue = "10") Integer pageSize) {
        // 非超管只能看本人发起的实例，忽略前端传入的任意发起人
        if (!flowAccessService.isSuperAdmin()) {
            initiatorUserId = flowAccessService.currentUserId();
        }
        long total = flowService.countProcessInstances(processKey, initiatorUserId);
        // 同 definitionPage：必须映射为 VO，否则运行中实例存在时序列化 Flowable 实体会 NPE → 500。
        // 坑在于"没有实例时这个接口是好的"，所以只有真的跑起流程才会暴露。
        List<WfProcessInstanceVO> list = flowService.listProcessInstances(processKey, initiatorUserId, (pageNo - 1) * pageSize, pageSize)
                .stream().map(WfProcessInstanceVO::from).toList();
        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("list", list);
        return R.success(result);
    }

    @SLog(title = "取消流程实例")
    @DeleteMapping("/instance/{processInstanceId}")
    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "flow:process:cancel")
    @Operation(summary = "取消流程实例")
    public R cancelInstance(@PathVariable("processInstanceId") String processInstanceId,
                            @RequestParam(required = false) String reason) {
        HistoricProcessInstance instance = flowService.getHistoricProcessInstance(processInstanceId);
        if (instance == null || instance.getEndTime() != null) {
            throw new BusinessException("流程实例不存在或已结束");
        }
        flowAccessService.checkProcessCancelable(processInstanceId, flowAccessService.currentUserId());
        flowService.cancelProcessInstance(processInstanceId, reason);
        return R.success();
    }
}
