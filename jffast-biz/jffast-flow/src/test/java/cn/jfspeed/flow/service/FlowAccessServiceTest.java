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

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 流程数据权限校验用例：候选人 / 办理人 / 发起人三种身份的放行与拒绝分支。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlowAccessServiceTest {

    private static final String USER_ID = "7";
    private static final String OTHER_USER = "8";
    private static final String TASK_ID = "task-1";
    private static final String PROCESS_ID = "process-1";

    @Mock
    private cn.jfspeed.flowable.service.FlowService flowService;

    @InjectMocks
    private FlowAccessService service;

    private MockedStatic<LoginUtil> loginUtil;
    private Task task;

    @BeforeEach
    void setUp() {
        loginUtil = mockStatic(LoginUtil.class);
        loginUtil.when(LoginUtil::getRoleCodeList).thenReturn(List.of());
        loginUtil.when(LoginUtil::getRoleIdList).thenReturn(List.of());
        task = org.mockito.Mockito.mock(Task.class);
        when(task.getId()).thenReturn(TASK_ID);
        when(task.getProcessInstanceId()).thenReturn(PROCESS_ID);
    }

    @AfterEach
    void tearDown() {
        loginUtil.close();
    }

    private void useSuperAdmin() {
        loginUtil.when(LoginUtil::getRoleCodeList).thenReturn(List.of("super_admin"));
    }

    private static BusinessException assertForbidden(Runnable action) {
        BusinessException ex = assertThrows(BusinessException.class, action::run);
        assertEquals(Integer.valueOf(403), ex.getCode());
        return ex;
    }

    @Nested
    @DisplayName("checkClaimable")
    class CheckClaimable {

        @Test
        @DisplayName("未签收且是候选人应放行")
        void candidateCanClaim() {
            when(flowService.isTaskCandidate(TASK_ID, USER_ID)).thenReturn(true);
            service.checkClaimable(task, USER_ID);
        }

        @Test
        @DisplayName("未签收且不是候选人应拒绝")
        void nonCandidateCannotClaim() {
            when(flowService.isTaskCandidate(TASK_ID, USER_ID)).thenReturn(false);
            assertForbidden(() -> service.checkClaimable(task, USER_ID));
        }

        @Test
        @DisplayName("已被他人签收应拒绝")
        void claimedByOtherCannotClaim() {
            when(task.getAssignee()).thenReturn(OTHER_USER);
            assertForbidden(() -> service.checkClaimable(task, USER_ID));
        }

        @Test
        @DisplayName("已由本人签收应幂等放行")
        void claimedBySelfCanClaimAgain() {
            when(task.getAssignee()).thenReturn(USER_ID);
            service.checkClaimable(task, USER_ID);
        }

        @Test
        @DisplayName("超管不受候选人限制")
        void superAdminCanClaim() {
            useSuperAdmin();
            when(task.getAssignee()).thenReturn(OTHER_USER);
            service.checkClaimable(task, USER_ID);
        }
    }

    @Nested
    @DisplayName("checkOperable")
    class CheckOperable {

        @Test
        @DisplayName("办理人是本人应放行")
        void assigneeCanOperate() {
            when(task.getAssignee()).thenReturn(USER_ID);
            service.checkOperable(task, USER_ID);
        }

        @Test
        @DisplayName("办理人不是本人应拒绝")
        void otherAssigneeCannotOperate() {
            when(task.getAssignee()).thenReturn(OTHER_USER);
            assertForbidden(() -> service.checkOperable(task, USER_ID));
        }

        @Test
        @DisplayName("未签收但属候选人应放行")
        void candidateCanOperate() {
            when(flowService.isTaskCandidate(TASK_ID, USER_ID)).thenReturn(true);
            service.checkOperable(task, USER_ID);
        }

        @Test
        @DisplayName("未签收且非候选人应拒绝")
        void nonCandidateCannotOperate() {
            when(flowService.isTaskCandidate(TASK_ID, USER_ID)).thenReturn(false);
            assertForbidden(() -> service.checkOperable(task, USER_ID));
        }

        @Test
        @DisplayName("超管可直接办理")
        void superAdminCanOperate() {
            useSuperAdmin();
            when(task.getAssignee()).thenReturn(OTHER_USER);
            service.checkOperable(task, USER_ID);
        }
    }

    @Nested
    @DisplayName("checkDelegable")
    class CheckDelegable {

        @Test
        @DisplayName("当前办理人可转办")
        void assigneeCanDelegate() {
            when(task.getAssignee()).thenReturn(USER_ID);
            service.checkDelegable(task, USER_ID);
        }

        @Test
        @DisplayName("未签收任务不可转办")
        void unclaimedCannotDelegate() {
            assertForbidden(() -> service.checkDelegable(task, USER_ID));
        }

        @Test
        @DisplayName("非当前办理人不可转办")
        void otherCannotDelegate() {
            when(task.getAssignee()).thenReturn(OTHER_USER);
            assertForbidden(() -> service.checkDelegable(task, USER_ID));
        }
    }

    @Nested
    @DisplayName("读取与取消")
    class ReadAndCancel {

        @Test
        @DisplayName("任务办理人可查看详情")
        void assigneeCanReadTask() {
            when(task.getAssignee()).thenReturn(USER_ID);
            service.checkTaskReadable(task, USER_ID);
        }

        @Test
        @DisplayName("流程发起人可查看任务详情")
        void initiatorCanReadTask() {
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(USER_ID);
            service.checkTaskReadable(task, USER_ID);
        }

        @Test
        @DisplayName("既非办理人/候选人/发起人不可查看任务")
        void outsiderCannotReadTask() {
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(OTHER_USER);
            assertForbidden(() -> service.checkTaskReadable(task, USER_ID));
        }

        @Test
        @DisplayName("流程参与人可查看流程实例")
        void participantCanReadProcess() {
            when(flowService.isProcessParticipant(PROCESS_ID, USER_ID)).thenReturn(true);
            service.checkProcessReadable(PROCESS_ID, USER_ID);
        }

        @Test
        @DisplayName("非参与人不可查看流程实例")
        void outsiderCannotReadProcess() {
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(OTHER_USER);
            when(flowService.isProcessParticipant(PROCESS_ID, USER_ID)).thenReturn(false);
            assertForbidden(() -> service.checkProcessReadable(PROCESS_ID, USER_ID));
        }

        @Test
        @DisplayName("发起人可取消流程")
        void initiatorCanCancelProcess() {
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(USER_ID);
            service.checkProcessCancelable(PROCESS_ID, USER_ID);
        }

        @Test
        @DisplayName("非发起人不可取消流程")
        void outsiderCannotCancelProcess() {
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(OTHER_USER);
            assertForbidden(() -> service.checkProcessCancelable(PROCESS_ID, USER_ID));
        }

        @Test
        @DisplayName("超管可取消任意流程")
        void superAdminCanCancelProcess() {
            useSuperAdmin();
            when(flowService.getProcessInitiatorUserId(PROCESS_ID)).thenReturn(OTHER_USER);
            service.checkProcessCancelable(PROCESS_ID, USER_ID);
        }
    }
}
