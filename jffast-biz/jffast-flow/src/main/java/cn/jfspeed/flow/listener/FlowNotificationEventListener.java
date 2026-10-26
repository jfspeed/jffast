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
package cn.jfspeed.flow.listener;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.flowable.service.FlowService;
import cn.jfspeed.system.constant.NotificationConstant;
import cn.jfspeed.system.service.SysNotificationService;

/**
 * 流程通知事件监听器。
 * <p>
 * 注册为 Flowable 引擎级全局事件监听器（见 {@code FlowNotificationAutoConfiguration}），
 * 把工作流事件转成站内通知，写入 {@code sys_notification} + {@code sys_notification_record} 并定向 WebSocket 下发：
 * <ul>
 *   <li>{@code TASK_CREATED} / {@code TASK_ASSIGNED} → 给办理人发「待办任务」通知</li>
 *   <li>{@code TASK_COMPLETED} → 给流程发起人发「审批进展」通知</li>
 *   <li>{@code PROCESS_COMPLETED} → 给流程发起人发「流程已办结」通知</li>
 * </ul>
 * <p>
 * <b>可靠性约定</b>：
 * <ol>
 *   <li>监听器在引擎事务内同步执行，因此内部<b>吞掉所有异常</b>且 {@link #isFailOnException()} 返回 false，
 *       通知失败绝不阻断流程流转；</li>
 *   <li>通知写入走 {@link SysNotificationService} 的独立事务（REQUIRES_NEW），
 *       即使写通知失败也只回滚通知自身，不会把引擎事务标记为 rollback-only。</li>
 * </ol>
 *
 * @author jffast
 */
public class FlowNotificationEventListener implements FlowableEventListener {

    private static final Logger log = LoggerFactory.getLogger(FlowNotificationEventListener.class);

    /**
     * 去重窗口（毫秒）。
     * <p>
     * Flowable 在「任务创建时已带办理人」的场景会连续派发 TASK_CREATED 与 TASK_ASSIGNED，
     * 两次都会命中待办分支。用 (taskId, assignee) 的短窗口去重避免给同一个人发两条相同的待办通知。
     */
    private static final long DEDUP_WINDOW_MILLIS = 5000L;

    /** 去重上限，超过则整体清理，防止长跑进程内存增长 */
    private static final int DEDUP_MAX_SIZE = 2000;

    private final ConcurrentHashMap<String, Long> recentNotified = new ConcurrentHashMap<>();

    private final FlowService flowService;

    /**
     * 通知服务。<b>可能为 null</b>——系统通知开关（jffast.system.notification.enabled）关闭时，
     * 监听器仍会注册（避免引擎配置分叉），但所有通知动作静默跳过。
     */
    private final SysNotificationService notificationService;

    public FlowNotificationEventListener(FlowService flowService, SysNotificationService notificationService) {
        this.flowService = flowService;
        this.notificationService = notificationService;
    }

    @Override
    public void onEvent(FlowableEvent event) {
        if (notificationService == null) {
            return;
        }
        try {
            if (event.getType() == FlowableEngineEventType.TASK_CREATED
                    || event.getType() == FlowableEngineEventType.TASK_ASSIGNED) {
                handleTaskTodo(event);
            } else if (event.getType() == FlowableEngineEventType.TASK_COMPLETED) {
                handleTaskCompleted(event);
            } else if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED
                    || event.getType() == FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT) {
                handleProcessCompleted(event);
            }
        } catch (Exception e) {
            // 通知属于旁路能力，绝不允许影响流程流转
            log.warn("流程通知处理失败，eventType={}", event.getType(), e);
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 待办：TASK_CREATED / TASK_ASSIGNED
    // ────────────────────────────────────────────────────────────────

    private void handleTaskTodo(FlowableEvent event) {
        Task task = extractTask(event);
        if (task == null) {
            return;
        }
        Long assigneeId = parseUserId(task.getAssignee());
        if (assigneeId == null) {
            // 任务池模式（仅候选组，无具体办理人）不推送个人待办
            return;
        }
        if (isDuplicate(task.getId(), task.getAssignee())) {
            return;
        }
        notificationService.sendToUser(
                assigneeId,
                "您有新的待办任务",
                "任务「" + StrUtil.blankToDefault(task.getName(), task.getId()) + "」已分配给您，请及时处理。",
                NotificationConstant.TYPE_FLOW,
                NotificationConstant.LEVEL_INFO);
    }

    // ────────────────────────────────────────────────────────────────
    // 审批进展：TASK_COMPLETED
    // ────────────────────────────────────────────────────────────────

    private void handleTaskCompleted(FlowableEvent event) {
        Task task = extractTask(event);
        if (task == null) {
            return;
        }
        FlowableEngineEvent engineEvent = (FlowableEngineEvent) event;
        Long initiatorId = parseUserId(flowService.getProcessInitiatorUserId(engineEvent.getProcessInstanceId()));
        if (initiatorId == null) {
            return;
        }
        // 自己办自己的节点不必再通知自己
        if (initiatorId.equals(parseUserId(task.getAssignee()))) {
            return;
        }
        String taskName = StrUtil.blankToDefault(task.getName(), task.getId());
        String operator = StrUtil.blankToDefault(task.getAssignee(), "他人");
        notificationService.sendToUser(
                initiatorId,
                "流程审批进展",
                "您发起的流程，节点「" + taskName + "」已由 " + operator + " 办理完成。",
                NotificationConstant.TYPE_FLOW,
                NotificationConstant.LEVEL_INFO);
    }

    // ────────────────────────────────────────────────────────────────
    // 办结：PROCESS_COMPLETED
    // ────────────────────────────────────────────────────────────────

    private void handleProcessCompleted(FlowableEvent event) {
        if (!(event instanceof FlowableEngineEvent engineEvent)) {
            return;
        }
        String processInstanceId = engineEvent.getProcessInstanceId();
        if (StrUtil.isBlank(processInstanceId)) {
            return;
        }
        Long initiatorId = parseUserId(flowService.getProcessInitiatorUserId(processInstanceId));
        if (initiatorId == null) {
            return;
        }
        notificationService.sendToUser(
                initiatorId,
                "流程已办结",
                "您发起的流程「" + resolveProcessName(processInstanceId) + "」已办结。",
                NotificationConstant.TYPE_FLOW,
                NotificationConstant.LEVEL_INFO);
    }

    // ────────────────────────────────────────────────────────────────
    // 辅助
    // ────────────────────────────────────────────────────────────────

    private String resolveProcessName(String processInstanceId) {
        try {
            HistoricProcessInstance instance = flowService.getHistoricProcessInstance(processInstanceId);
            if (instance == null) {
                return processInstanceId;
            }
            return StrUtil.blankToDefault(instance.getProcessDefinitionName(), instance.getProcessDefinitionKey());
        } catch (Exception e) {
            log.debug("获取流程名称失败，processInstanceId={}", processInstanceId, e);
            return processInstanceId;
        }
    }

    private Task extractTask(FlowableEvent event) {
        if (event instanceof FlowableEntityEvent entityEvent && entityEvent.getEntity() instanceof Task task) {
            return task;
        }
        return null;
    }

    private Long parseUserId(String userId) {
        if (StrUtil.isBlank(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            // 审批人可能是用户名等非数值标识，此时无法关联 sys_user，跳过通知
            log.debug("流程办理人非数值用户ID，跳过通知：{}", userId);
            return null;
        }
    }

    private boolean isDuplicate(String taskId, String assignee) {
        if (StrUtil.isBlank(taskId)) {
            return false;
        }
        String key = taskId + ":" + assignee;
        long now = System.currentTimeMillis();
        if (recentNotified.size() > DEDUP_MAX_SIZE) {
            recentNotified.clear();
        }
        Long last = recentNotified.put(key, now);
        return last != null && now - last < DEDUP_WINDOW_MILLIS;
    }

    // ────────────────────────────────────────────────────────────────
    // FlowableEventListener 契约
    // ────────────────────────────────────────────────────────────────

    /**
     * 只订阅通知相关事件，避免引擎把全部事件都回调进来。
     */
    @Override
    public Collection<? extends FlowableEventType> getTypes() {
        return Set.of(
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_ASSIGNED,
                FlowableEngineEventType.TASK_COMPLETED,
                FlowableEngineEventType.PROCESS_COMPLETED,
                FlowableEngineEventType.PROCESS_COMPLETED_WITH_TERMINATE_END_EVENT);
    }

    /**
     * 监听器内部已兜底异常，不允许它影响引擎事务。
     */
    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
