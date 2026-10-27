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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.system.constant.NotificationConstant;
import cn.jfspeed.system.entity.SysNotification;
import cn.jfspeed.system.entity.SysNotificationRecord;
import cn.jfspeed.system.entity.SysUser;
import cn.jfspeed.system.entity.SysUserDept;
import cn.jfspeed.system.entity.SysUserRole;
import cn.jfspeed.system.mapper.SysNotificationMapper;
import cn.jfspeed.system.mapper.SysUserDeptMapper;
import cn.jfspeed.system.mapper.SysUserMapper;
import cn.jfspeed.system.mapper.SysUserRoleMapper;
import cn.jfspeed.system.notify.NotificationChannelDispatcher;
import cn.jfspeed.web.websocket.WebSocketService;

/**
 * 通知发送的接收人解析契约（fail-closed）。
 *
 * <p>三个方向都要钉住：
 * <ul>
 *   <li><b>类型缺失不能兜底成「全体用户」</b>：{@code recipientType} 为空时旧实现按 all 处理，
 *       等于「参数没填就默认给全站发一条通知」——一次无意的全员广播；</li>
 *   <li><b>类型非法要拒绝</b>：拼错的值（如 {@code everyone}）旧实现静默解析出 0 人，
 *       却照样把通知记成「已发送」；</li>
 *   <li><b>解析不到人不能落「已发送」记录</b>：接收人解析必须在落库<b>之前</b>，
 *       0 人时整条通知应当被拒绝，而不是留下一条永远没人收到的「已发送」记录。</li>
 * </ul>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysNotificationRecipientValidationTest {

    @Mock
    private SysNotificationMapper baseMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysUserRoleMapper sysUserRoleMapper;

    @Mock
    private SysUserDeptMapper sysUserDeptMapper;

    @Mock
    private SysNotificationRecordService sysNotificationRecordService;

    @Mock
    private NotificationChannelDispatcher notificationChannelDispatcher;

    @Mock
    private ObjectProvider<WebSocketService> webSocketServiceProvider;

    @InjectMocks
    private SysNotificationService service;

    @BeforeAll
    static void initTableInfoCache() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    void setUp() {
        // 本类有唯一带参构造器（ObjectProvider），Mockito 只会走构造器注入，
        // 其余 @Resource 字段需要显式塞进去。
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "sysUserRoleMapper", sysUserRoleMapper);
        ReflectionTestUtils.setField(service, "sysUserDeptMapper", sysUserDeptMapper);
        ReflectionTestUtils.setField(service, "sysNotificationRecordService", sysNotificationRecordService);
        ReflectionTestUtils.setField(service, "notificationChannelDispatcher", notificationChannelDispatcher);
        when(webSocketServiceProvider.getIfAvailable()).thenReturn(null);
    }

    private static SysNotification notification(String recipientType, String recipientIds) {
        SysNotification notification = new SysNotification();
        notification.setTitle("测试通知");
        notification.setContent("内容");
        notification.setRecipientType(recipientType);
        notification.setRecipientIds(recipientIds);
        return notification;
    }

    private static SysUser user(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("接收人类型为空必须拒绝，且不得触发全员查询")
    void blankRecipientTypeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendNotification(notification(null, null)));
        assertTrue(ex.getMessage().contains("接收人类型"), "实际提示：" + ex.getMessage());

        verify(sysUserMapper, never()).selectList(any());
        verify(baseMapper, never()).insert(any(SysNotification.class));
        verify(baseMapper, never()).updateById(any(SysNotification.class));
    }

    @Test
    @DisplayName("接收人类型非法必须拒绝（拼错的值不能静默变成 0 人接收）")
    void unknownRecipientTypeIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendNotification(notification("everyone", "1,2")));
        assertTrue(ex.getMessage().contains("不合法"), "实际提示：" + ex.getMessage());

        verify(baseMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    @DisplayName("非 all 类型但未选接收人必须拒绝")
    void nonAllTypeWithoutRecipientIdsIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendNotification(notification(NotificationConstant.RECIPIENT_ROLE, null)));
        assertTrue(ex.getMessage().contains("请选择接收人"), "实际提示：" + ex.getMessage());

        verify(baseMapper, never()).insert(any(SysNotification.class));
    }

    @Test
    @DisplayName("解析不到接收人时整条通知被拒绝，且不留下「已发送」记录")
    void unresolvedRecipientLeavesNoSentRecord() {
        when(sysUserRoleMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.sendNotification(notification(NotificationConstant.RECIPIENT_ROLE, "9")));
        assertTrue(ex.getMessage().contains("未解析到接收人"), "实际提示：" + ex.getMessage());

        verify(baseMapper, never()).insert(any(SysNotification.class));
        verify(baseMapper, never()).updateById(any(SysNotification.class));
        verify(sysNotificationRecordService, never()).saveBatch(anyList(), anyInt());
    }

    @Test
    @DisplayName("全员广播：解析成功才落库并置为已发送，且每人一条站内信记录")
    void allTypeBroadcastResolvesAndPersists() {
        when(sysUserMapper.selectList(any())).thenReturn(Arrays.asList(user(1L), user(2L)));
        when(baseMapper.insert(any(SysNotification.class))).thenAnswer(invocation -> {
            SysNotification inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });

        service.sendNotification(notification(NotificationConstant.RECIPIENT_ALL, null));

        ArgumentCaptor<SysNotification> inserted = ArgumentCaptor.forClass(SysNotification.class);
        verify(baseMapper).insert(inserted.capture());
        assertEquals(NotificationConstant.STATUS_SENT, inserted.getValue().getStatus());
        assertNotNull(inserted.getValue().getSendTime());

        ArgumentCaptor<List<SysNotificationRecord>> records = ArgumentCaptor.forClass(List.class);
        verify(sysNotificationRecordService).saveBatch(records.capture(), anyInt());
        List<SysNotificationRecord> saved = records.getValue();
        assertEquals(2, saved.size());
        assertEquals(Arrays.asList(1L, 2L),
                saved.stream().map(SysNotificationRecord::getUserId).sorted().toList());
    }

    @Test
    @DisplayName("按角色定向：解析到该角色的用户才发送")
    void roleTypeResolvesOnlyMembersOfRole() {
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(7L);
        userRole.setRoleId(9L);
        when(sysUserRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(7L)));
        when(baseMapper.insert(any(SysNotification.class))).thenAnswer(invocation -> {
            SysNotification inserted = invocation.getArgument(0);
            inserted.setId(101L);
            return 1;
        });

        service.sendNotification(notification(NotificationConstant.RECIPIENT_ROLE, "9"));

        ArgumentCaptor<List<SysNotificationRecord>> records = ArgumentCaptor.forClass(List.class);
        verify(sysNotificationRecordService).saveBatch(records.capture(), anyInt());
        assertEquals(List.of(7L),
                records.getValue().stream().map(SysNotificationRecord::getUserId).toList());
    }

    @Test
    @DisplayName("按部门定向：解析到该部门的用户才发送")
    void deptTypeResolvesOnlyMembersOfDept() {
        SysUserDept userDept = new SysUserDept();
        userDept.setUserId(8L);
        userDept.setDeptId(3L);
        when(sysUserDeptMapper.selectList(any())).thenReturn(List.of(userDept));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user(8L)));
        when(baseMapper.insert(any(SysNotification.class))).thenAnswer(invocation -> {
            SysNotification inserted = invocation.getArgument(0);
            inserted.setId(102L);
            return 1;
        });

        service.sendNotification(notification(NotificationConstant.RECIPIENT_DEPT, "3"));

        ArgumentCaptor<List<SysNotificationRecord>> records = ArgumentCaptor.forClass(List.class);
        verify(sysNotificationRecordService).saveBatch(records.capture(), anyInt());
        assertEquals(List.of(8L),
                records.getValue().stream().map(SysNotificationRecord::getUserId).toList());
    }
}
