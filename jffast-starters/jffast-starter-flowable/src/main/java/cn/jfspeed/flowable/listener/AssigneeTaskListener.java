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
package cn.jfspeed.flowable.listener;

import cn.jfspeed.flowable.constant.FlowConstant;
import cn.jfspeed.flowable.resolver.AssigneeResolver;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 审批人解析任务监听器。
 * <p>
 * BPMN UserTask 配置（任务级扩展属性，流程设计器画布即按此产出）：
 * <pre>{@code
 * <userTask id="approveTask" name="审批"
 *           flowable:assigneeType="role" flowable:assigneeValue="manager">
 *   <extensionElements>
 *     <flowable:taskListener event="create" delegateExpression="${assigneeTaskListener}"/>
 *   </extensionElements>
 * </userTask>
 * }</pre>
 * <p>
 * 审批人来源两级（同源取值，避免跨源拼接）：
 * <ol>
 *   <li><b>任务级 BPMN 扩展属性</b>（优先）：{@code flowable:assigneeType}/{@code flowable:assigneeValue}，
 *       逐任务生效，设计器可视化面板直接配置；</li>
 *   <li><b>流程变量</b>（兜底）：{@code assigneeType}/{@code assigneeValue}，业务层在发起流程
 *       或前置任务完成时写入（兼容既有接入）。</li>
 * </ol>
 * 取值语义：{@code assigneeType}=user/role/dept/initiator；
 * {@code assigneeValue}=用户ID/角色key/部门ID（initiator 时忽略）。
 * 单人 → setAssignee；多人 → addCandidateUser（任务池模式）。
 *
 * @author jffast
 */
public class AssigneeTaskListener implements TaskListener {

    private static final Logger log = LoggerFactory.getLogger(AssigneeTaskListener.class);
    private static final long serialVersionUID = 1L;

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final AssigneeResolver assigneeResolver;

    /**
     * 构造器：assigneeResolver 可为 null（仅支持 user/initiator 类型）。
     */
    public AssigneeTaskListener(RuntimeService runtimeService,
                                TaskService taskService,
                                RepositoryService repositoryService,
                                AssigneeResolver assigneeResolver) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.assigneeResolver = assigneeResolver;
    }

    @Override
    public void notify(DelegateTask delegateTask) {
        AssigneeConfig config = resolveConfig(delegateTask);
        if (config == null) {
            return;
        }
        List<String> userIds = resolve(delegateTask.getExecutionId(), config.type(), config.value());
        if (userIds.isEmpty()) {
            log.warn("审批人解析为空：type={}, value={}, taskId={}", config.type(), config.value(), delegateTask.getId());
            return;
        }
        if (userIds.size() == 1) {
            taskService.setAssignee(delegateTask.getId(), userIds.get(0));
        } else {
            for (String userId : userIds) {
                taskService.addCandidateUser(delegateTask.getId(), userId);
            }
        }
        log.debug("任务 {} 审批人解析：type={}, userIds={}", delegateTask.getId(), config.type(), userIds);
    }

    /**
     * 解析审批人配置：任务级扩展属性优先，其次流程变量；两者都无则返回 null（不分配）。
     */
    private AssigneeConfig resolveConfig(DelegateTask delegateTask) {
        UserTask userTask = resolveTaskElement(delegateTask);
        if (userTask != null) {
            String type = taskAttribute(userTask, FlowConstant.EXT_ATTR_ASSIGNEE_TYPE);
            if (type != null && !type.isBlank()) {
                String value = taskAttribute(userTask, FlowConstant.EXT_ATTR_ASSIGNEE_VALUE);
                log.debug("任务 {} 使用任务级扩展属性解析审批人：type={}", delegateTask.getId(), type);
                return new AssigneeConfig(type, value);
            }
        }
        String type = (String) runtimeService.getVariable(delegateTask.getExecutionId(), FlowConstant.EXT_ATTR_ASSIGNEE_TYPE);
        if (type == null) {
            return null;
        }
        String value = (String) runtimeService.getVariable(delegateTask.getExecutionId(), FlowConstant.EXT_ATTR_ASSIGNEE_VALUE);
        return new AssigneeConfig(type, value);
    }

    /**
     * 定位当前任务对应的 BPMN UserTask 元素。取不到（无定义上下文/非 UserTask/模型缺失）
     * 或读取异常时返回 null，由调用方回退流程变量——属性读取失败绝不阻塞任务创建。
     */
    private UserTask resolveTaskElement(DelegateTask delegateTask) {
        String processDefinitionId = delegateTask.getProcessDefinitionId();
        String taskDefinitionKey = delegateTask.getTaskDefinitionKey();
        if (repositoryService == null
                || isBlank(processDefinitionId) || isBlank(taskDefinitionKey)) {
            return null;
        }
        try {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
                return null;
            }
            FlowElement element = bpmnModel.getMainProcess().getFlowElement(taskDefinitionKey);
            return element instanceof UserTask userTask ? userTask : null;
        } catch (RuntimeException ex) {
            log.warn("读取任务级审批人扩展属性失败，回退流程变量：taskId={}", delegateTask.getId(), ex);
            return null;
        }
    }

    private String taskAttribute(UserTask userTask, String name) {
        List<ExtensionAttribute> attributes = userTask.getAttributes().get(name);
        return attributes == null || attributes.isEmpty() ? null : attributes.get(0).getValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> resolve(String executionId, String type, String value) {
        switch (type) {
            case FlowConstant.ASSIGNEE_TYPE_INITIATOR:
                String initiatorId = (String) runtimeService.getVariable(executionId, FlowConstant.VAR_INITIATOR_USER_ID);
                return initiatorId == null ? Collections.emptyList() : Collections.singletonList(initiatorId);
            case FlowConstant.ASSIGNEE_TYPE_USER:
                return split(value);
            case FlowConstant.ASSIGNEE_TYPE_ROLE:
            case FlowConstant.ASSIGNEE_TYPE_DEPT:
                if (assigneeResolver == null) {
                    log.warn("审批人类型 {} 需要 AssigneeResolver 实现，容器未注册，跳过", type);
                    return Collections.emptyList();
                }
                return assigneeResolver.resolveUserIds(type, value);
            default:
                log.warn("未知审批人类型：{}", type);
                return Collections.emptyList();
        }
    }

    private List<String> split(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String part : Arrays.asList(value.split(","))) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 审批人配置（类型 + 取值），来源单一：要么同取任务级扩展属性，要么同取流程变量。
     */
    private record AssigneeConfig(String type, String value) {
    }
}
