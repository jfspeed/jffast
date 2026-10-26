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
package cn.jfspeed.flow.service;

import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.flowable.service.FlowService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;

/**
 * 流程数据权限校验：任务只能由候选人/办理人操作，流程实例只能由发起人或参与人查看。
 *
 * @author jffast
 */
@Service
public class FlowAccessService {

    private final FlowService flowService;

    public FlowAccessService(FlowService flowService) {
        this.flowService = flowService;
    }

    public String currentUserId() {
        return String.valueOf(LoginUtil.getLoginUserId());
    }

    public boolean isSuperAdmin() {
        return LoginUtil.getRoleCodeList().contains(BaseConstant.ROLE_SUPER_ADMIN)
                || LoginUtil.getRoleIdList().contains(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG);
    }

    /** 签收：未签收时必须是候选人；已签收时只有本人可重复签收（幂等）。 */
    public void checkClaimable(Task task, String userId) {
        if (isSuperAdmin()) {
            return;
        }
        if (StrUtil.isNotBlank(task.getAssignee())) {
            if (task.getAssignee().equals(userId)) {
                return;
            }
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "任务已被他人签收");
        }
        if (!flowService.isTaskCandidate(task.getId(), userId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权签收该任务：不在候选人范围");
        }
    }

    /** 办理：已签收任务仅办理人可完成；未签收任务需在候选人范围内。 */
    public void checkOperable(Task task, String userId) {
        if (isSuperAdmin()) {
            return;
        }
        if (StrUtil.isNotBlank(task.getAssignee())) {
            if (!task.getAssignee().equals(userId)) {
                throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权办理该任务：当前办理人不是本人");
            }
            return;
        }
        if (!flowService.isTaskCandidate(task.getId(), userId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权办理该任务：不在候选人范围");
        }
    }

    /** 转办：仅当前办理人可转办。 */
    public void checkDelegable(Task task, String userId) {
        if (isSuperAdmin()) {
            return;
        }
        if (StrUtil.isBlank(task.getAssignee()) || !task.getAssignee().equals(userId)) {
            throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权转办该任务：当前办理人不是本人");
        }
    }

    /** 任务详情/审批历史：办理人、候选人或流程发起人可见。 */
    public void checkTaskReadable(Task task, String userId) {
        if (isSuperAdmin()
                || userId.equals(task.getAssignee())
                || flowService.isTaskCandidate(task.getId(), userId)
                || userId.equals(flowService.getProcessInitiatorUserId(task.getProcessInstanceId()))) {
            return;
        }
        throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权查看该任务");
    }

    /** 流程实例详情：发起人或参与过审批的用户可见。 */
    public void checkProcessReadable(String processInstanceId, String userId) {
        if (isSuperAdmin()
                || userId.equals(flowService.getProcessInitiatorUserId(processInstanceId))
                || flowService.isProcessParticipant(processInstanceId, userId)) {
            return;
        }
        throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权查看该流程实例");
    }

    /** 取消流程实例：仅发起人（或超管）可取消。 */
    public void checkProcessCancelable(String processInstanceId, String userId) {
        if (isSuperAdmin() || userId.equals(flowService.getProcessInitiatorUserId(processInstanceId))) {
            return;
        }
        throw new BusinessException(AuthErrorCode.FORBIDDEN.getCode(), "无权取消该流程实例");
    }
}
