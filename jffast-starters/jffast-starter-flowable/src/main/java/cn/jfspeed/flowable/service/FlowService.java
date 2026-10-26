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
package cn.jfspeed.flowable.service;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.flowable.constant.FlowConstant;
import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * 工作流服务门面。
 * <p>
 * 封装 Flowable 的 Repository/Runtime/Task/History 四大核心服务，
 * 业务模块（jffast-flow）注入此 Bean 即可完成全部工作流操作，
 * 无需直接依赖 Flowable API。
 * <p>
 * 由 {@code FlowableAutoConfiguration} 通过 {@code @Bean} 注册
 * （遵循 Starter 规范：禁止 @Component/@Service 双重注册）。
 *
 * @author jffast
 */
public class FlowService {

    /** BPMN 部署资源文件名白名单：常规字符 + .bpmn20.xml / .bpmn 后缀，杜绝路径穿越 */
    private static final Pattern RESOURCE_NAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_.\\-]+\\.(bpmn20\\.xml|bpmn)$");

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final IdentityService identityService;

    public FlowService(RepositoryService repositoryService,
                       RuntimeService runtimeService,
                       TaskService taskService,
                       HistoryService historyService,
                       IdentityService identityService) {
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
        this.identityService = identityService;
    }

    // ─── 流程部署 ──────────────────────────────────────

    /**
     * 部署 BPMN XML 字符串。
     *
     * @param name         部署名称
     * @param category     流程分类（对应 {@code wf_category.category_code}，可为 null）
     * @param resourceName 资源文件名
     * @param bpmnXml      BPMN XML 内容
     */
    public Deployment deploy(String name, String category, String resourceName, String bpmnXml) {
        validateResourceName(resourceName);
        validateBpmnXml(bpmnXml);
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addString(resourceName, bpmnXml)
                .deploy();
        applyCategory(deployment, category);
        return deployment;
    }

    /**
     * 部署 BPMN 输入流。
     */
    public Deployment deploy(String name, String category, String resourceName, InputStream bpmnStream) {
        validateResourceName(resourceName);
        byte[] bytes;
        try {
            bytes = bpmnStream.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException("读取 BPMN 输入流失败：" + e.getMessage());
        }
        // 与字符串重载走同一道预校验（禁用 DTD/外部实体 + 根元素必须 definitions）：
        // 少了这一步，该入口就能把未经检查的 XML 直接交给引擎解析。
        validateBpmnXml(new String(bytes, StandardCharsets.UTF_8));
        Deployment deployment = repositoryService.createDeployment()
                .name(name)
                .addInputStream(resourceName, new ByteArrayInputStream(bytes))
                .deploy();
        applyCategory(deployment, category);
        return deployment;
    }

    /**
     * 校验 BPMN 资源文件名：仅允许常规字符 + .bpmn20.xml / .bpmn 后缀，
     * 并显式拒绝路径穿越（{@code ..} 与路径分隔符），避免落库资源名被当作路径使用。
     */
    private void validateResourceName(String resourceName) {
        if (resourceName == null || resourceName.isBlank()) {
            throw new BusinessException("BPMN 资源文件名不能为空");
        }
        if (resourceName.contains("..") || resourceName.contains("/") || resourceName.contains("\\")
                || !RESOURCE_NAME_PATTERN.matcher(resourceName).matches()) {
            throw new BusinessException("BPMN 资源文件名不合法（仅允许字母数字._- 且以 .bpmn20.xml/.bpmn 结尾）：" + resourceName);
        }
    }

    /**
     * 部署前预校验 BPMN XML：解析为安全 DOM（禁用 DTD / 外部实体防 XXE），
     * 根元素必须为 BPMN {@code definitions}。畸形或非 BPMN 内容直接拒绝，
     * 避免把无意义/恶意 XML 交给引擎部署。
     */
    private void validateBpmnXml(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new BusinessException("BPMN XML 内容不能为空");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();
            if (root == null || !"definitions".equals(root.getLocalName())) {
                throw new BusinessException("BPMN XML 根元素必须为 definitions");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("BPMN XML 解析失败：" + e.getMessage());
        }
    }

    /**
     * 把业务分类（{@code wf_category.category_code}）写到部署产生的流程定义上。
     * <p>
     * Flowable 的流程定义 {@code category} 默认取自 BPMN 的 {@code targetNamespace}（见
     * {@code ProcessParseHandler}），并非部署的 category；而前端「流程定义」页的分类列/筛选
     * 用的是 {@code wf_category.category_code}，因此必须显式覆盖才能对应上。
     */
    private void applyCategory(Deployment deployment, String category) {
        if (category == null || category.isEmpty()) {
            return;
        }
        repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list()
                .forEach(def -> repositoryService.setProcessDefinitionCategory(def.getId(), category));
    }

    /**
     * 删除部署（级联删除流程实例/任务）。
     */
    public void deleteDeployment(String deploymentId, boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
    }

    /**
     * 获取部署的 BPMN XML 内容（用于流程图查看/设计器回显）。
     */
    public String getBpmnXml(String processDefinitionId) {
        try (InputStream in = repositoryService.getProcessModel(processDefinitionId)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("读取 BPMN XML 失败：" + processDefinitionId, e);
        }
    }

    /**
     * 获取流程定义资源图片（PNG/SVG，用于流程图展示）。
     */
    public InputStream getProcessDiagram(String processDefinitionId) {
        return repositoryService.getProcessDiagram(processDefinitionId);
    }

    /**
     * 获取运行中实例的流程图（不含高亮，高亮需前端用 bpmn-js 渲染）。
     */
    public InputStream getInstanceDiagram(String processInstanceId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (instance == null || instance.getProcessDefinitionId() == null) {
            return null;
        }
        return repositoryService.getProcessDiagram(instance.getProcessDefinitionId());
    }

    // ─── 流程定义 ──────────────────────────────────────

    /**
     * 分页查询流程定义（最新版本）。
     */
    public List<ProcessDefinition> listProcessDefinitions(String processKey, String categoryName, Integer firstResult, Integer maxResults) {
        var query = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKeyLike("%" + processKey + "%");
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            query.processDefinitionCategory(categoryName);
        }
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    /**
     * 流程定义总数。
     */
    public long countProcessDefinitions(String processKey, String categoryName) {
        var query = repositoryService.createProcessDefinitionQuery().latestVersion();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKeyLike("%" + processKey + "%");
        }
        if (categoryName != null && !categoryName.isEmpty()) {
            query.processDefinitionCategory(categoryName);
        }
        return query.count();
    }

    public ProcessDefinition getProcessDefinition(String processDefinitionId) {
        return repositoryService.getProcessDefinition(processDefinitionId);
    }

    public ProcessDefinition getProcessDefinitionByKey(String processKey) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
    }

    /**
     * 查询流程定义名称。
     * <p>
     * Flowable 的任务实体（{@code Task}/{@code HistoricTaskInstance}）只带
     * {@code processDefinitionId} 而不含名称，任务列表要展示"流程名称"时用此方法补上。
     *
     * @return 流程定义名称；查不到返回 {@code null}
     */
    public String getProcessDefinitionName(String processDefinitionId) {
        if (processDefinitionId == null || processDefinitionId.isEmpty()) {
            return null;
        }
        ProcessDefinition definition = repositoryService.getProcessDefinition(processDefinitionId);
        return definition == null ? null : definition.getName();
    }

    /**
     * 挂起流程定义（暂停所有实例）。
     */
    public void suspendProcessDefinition(String processDefinitionId) {
        repositoryService.suspendProcessDefinitionById(processDefinitionId, true, null);
    }

    /**
     * 激活流程定义。
     */
    public void activateProcessDefinition(String processDefinitionId) {
        repositoryService.activateProcessDefinitionById(processDefinitionId, true, null);
    }

    // ─── 流程实例 ──────────────────────────────────────

    /**
     * 发起流程。
     *
     * @param processKey      流程定义 key
     * @param businessKey     业务主键（如请假单 ID）
     * @param initiatorUserId 发起人用户 ID（写入流程变量 + Flowable 认证用户）
     * @param variables       流程变量（含 assigneeType/assigneeValue 等），可为 null
     */
    public ProcessInstance startProcess(String processKey, String businessKey, String initiatorUserId, Map<String, Object> variables) {
        Map<String, Object> vars = variables == null ? new HashMap<>() : variables;
        vars.put(FlowConstant.VAR_INITIATOR_USER_ID, initiatorUserId);
        // 设置 Flowable 认证用户：引擎在 startProcessInstanceByKey 时会把发起人写入流程实例
        // 的 startUserId（及 START_USER_ID_ 变量），否则 ProcessInstance.startUserId 为 null、
        // startedBy 过滤失效（前端"发起人"列空白、按发起人搜索无效）。ThreadLocal，用完必须清理。
        identityService.setAuthenticatedUserId(initiatorUserId);
        try {
            return runtimeService.startProcessInstanceByKey(processKey, businessKey, vars);
        } finally {
            identityService.setAuthenticatedUserId(null);
        }
    }

    /**
     * 分页查询运行中的流程实例。
     */
    public List<ProcessInstance> listProcessInstances(String processKey, String initiatorUserId, Integer firstResult, Integer maxResults) {
        var query = runtimeService.createProcessInstanceQuery()
                .orderByProcessInstanceId().desc();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKey(processKey);
        }
        if (initiatorUserId != null && !initiatorUserId.isEmpty()) {
            query.startedBy(initiatorUserId);
        }
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    /**
     * 运行中流程实例总数。
     */
    public long countProcessInstances(String processKey, String initiatorUserId) {
        var query = runtimeService.createProcessInstanceQuery();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKey(processKey);
        }
        if (initiatorUserId != null && !initiatorUserId.isEmpty()) {
            query.startedBy(initiatorUserId);
        }
        return query.count();
    }

    /**
     * 历史流程实例总数。
     */
    public long countHistoricProcessInstances(String processKey, String initiatorUserId) {
        var query = historyService.createHistoricProcessInstanceQuery();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKey(processKey);
        }
        if (initiatorUserId != null && !initiatorUserId.isEmpty()) {
            query.startedBy(initiatorUserId);
        }
        return query.count();
    }

    /**
     * 候选任务总数（任务池模式）。
     */
    public long countCandidateTasks(String userId) {
        return taskService.createTaskQuery().taskCandidateUser(userId).count();
    }

    /**
     * 取消流程实例。
     */
    public void cancelProcessInstance(String processInstanceId, String reason) {
        runtimeService.deleteProcessInstance(processInstanceId, reason);
    }

    /**
     * 挂起流程实例。
     */
    public void suspendProcessInstance(String processInstanceId) {
        runtimeService.suspendProcessInstanceById(processInstanceId);
    }

    // ─── 任务 ──────────────────────────────────────────

    /**
     * 查询待办任务（按办理人）。
     */
    public List<Task> listTodoTasks(String assignee, Integer firstResult, Integer maxResults) {
        TaskQuery query = taskService.createTaskQuery()
                .taskAssignee(assignee)
                .orderByTaskCreateTime().desc();
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    /**
     * 查询待办任务（按候选办理人，任务池模式）。
     */
    public List<Task> listCandidateTasks(String userId, Integer firstResult, Integer maxResults) {
        TaskQuery query = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .orderByTaskCreateTime().desc();
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    public long countTodoTasks(String assignee) {
        return taskService.createTaskQuery().taskAssignee(assignee).count();
    }

    /**
     * 签收任务（任务池模式：从候选办理人变为指定办理人）。
     */
    public void claimTask(String taskId, String userId) {
        taskService.claim(taskId, userId);
    }

    /**
     * 完成任务（带审批意见）。
     *
     * @param taskId   任务 ID
     * @param userId   办理人
     * @param comment  审批意见
     * @param variables 流程变量（可含下一节点审批人配置）
     */
    public void completeTask(String taskId, String userId, String comment, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已完成：" + taskId);
        }
        if (comment != null && !comment.isEmpty()) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        if (variables != null && !variables.isEmpty()) {
            taskService.complete(taskId, variables);
        } else {
            taskService.complete(taskId);
        }
    }

    /**
     * 转办任务。
     */
    public void delegateTask(String taskId, String toUserId) {
        taskService.setAssignee(taskId, toUserId);
    }

    public Task getTask(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).singleResult();
    }

    /**
     * 指定用户是否为该任务的候选人（任务池模式）。
     */
    public boolean isTaskCandidate(String taskId, String userId) {
        return taskService.createTaskQuery()
                .taskId(taskId)
                .taskCandidateUser(userId)
                .count() > 0;
    }

    /**
     * 指定用户是否参与过该流程实例（任一历史任务的办理人）。
     */
    public boolean isProcessParticipant(String processInstanceId, String userId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(userId)
                .count() > 0;
    }

    // ─── 历史 ──────────────────────────────────────────

    /**
     * 查询已办任务（按办理人）。
     */
    public List<HistoricTaskInstance> listDoneTasks(String assignee, Integer firstResult, Integer maxResults) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(assignee)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc();
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    public long countDoneTasks(String assignee) {
        return historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(assignee)
                .finished()
                .count();
    }

    /**
     * 查询历史流程实例。
     */
    public List<HistoricProcessInstance> listHistoricProcessInstances(String processKey, String initiatorUserId, Integer firstResult, Integer maxResults) {
        var query = historyService.createHistoricProcessInstanceQuery()
                .orderByProcessInstanceStartTime().desc();
        if (processKey != null && !processKey.isEmpty()) {
            query.processDefinitionKey(processKey);
        }
        if (initiatorUserId != null && !initiatorUserId.isEmpty()) {
            query.startedBy(initiatorUserId);
        }
        if (firstResult != null && maxResults != null) {
            return query.listPage(firstResult, maxResults);
        }
        return query.list();
    }

    /**
     * 查询流程实例的审批历史（任务列表）。
     */
    public List<HistoricTaskInstance> listProcessHistory(String processInstanceId) {
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list();
    }

    public HistoricProcessInstance getHistoricProcessInstance(String processInstanceId) {
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
    }

    /**
     * 查询流程实例的发起人用户 ID。
     * <p>
     * 发起人由 {@link #startProcess} 写入流程变量 {@link FlowConstant#VAR_INITIATOR_USER_ID}，
     * 这里从历史变量中读取——流程办结（PROCESS_COMPLETED）后运行时变量已随实例删除，只有历史变量仍可查。
     *
     * @return 发起人用户 ID（字符串形式）；查不到返回 {@code null}
     */
    public String getProcessInitiatorUserId(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return null;
        }
        HistoricVariableInstance variable = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(FlowConstant.VAR_INITIATOR_USER_ID)
                .singleResult();
        return variable == null || variable.getValue() == null ? null : String.valueOf(variable.getValue());
    }
}
