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
package cn.jfspeed.ai.service;

import cn.jfspeed.ai.entity.AiConversation;
import cn.jfspeed.ai.mapper.AiConversationMapper;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.util.LoginUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 对话归属校验用例（仅本人或超管可读/删）。
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiConversationServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 99L;

    @Mock
    private AiMessageService messageService;

    @Mock
    private AiConversationMapper baseMapper;

    @InjectMocks
    private AiConversationService service;

    private MockedStatic<LoginUtil> loginUtil;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        loginUtil = mockStatic(LoginUtil.class);
    }

    @AfterEach
    void tearDown() {
        loginUtil.close();
    }

    private void loginAs(Long userId) {
        loginUtil.when(LoginUtil::getLoginUserId).thenReturn(userId);
        loginUtil.when(LoginUtil::getRoleCodeList).thenReturn(List.of());
        loginUtil.when(LoginUtil::getRoleIdList).thenReturn(List.of());
    }

    private AiConversation conversation(Long id, Long ownerId) {
        AiConversation conversation = new AiConversation();
        conversation.setId(id);
        conversation.setUserId(ownerId);
        return conversation;
    }

    @Test
    @DisplayName("本人可读取自己的对话")
    void ownerCanReadConversation() {
        loginAs(OWNER_ID);
        AiConversation conversation = conversation(1L, OWNER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation);

        assertSame(conversation, service.getOwnedConversation(1L));
    }

    @Test
    @DisplayName("非本人且非超管读取应抛 403")
    void nonOwnerCannotReadConversation() {
        loginAs(OWNER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation(1L, OTHER_ID));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getOwnedConversation(1L));
        assertEquals(Integer.valueOf(403), ex.getCode());
    }

    @Test
    @DisplayName("超管（角色码）可读取任意对话")
    void superAdminByRoleCodeCanReadConversation() {
        loginUtil.when(LoginUtil::getLoginUserId).thenReturn(OWNER_ID);
        loginUtil.when(LoginUtil::getRoleCodeList).thenReturn(List.of("super_admin"));
        loginUtil.when(LoginUtil::getRoleIdList).thenReturn(List.of());
        AiConversation conversation = conversation(1L, OTHER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation);

        assertSame(conversation, service.getOwnedConversation(1L));
    }

    @Test
    @DisplayName("超管（角色 ID=1）可读取任意对话")
    void superAdminByRoleIdCanReadConversation() {
        loginUtil.when(LoginUtil::getLoginUserId).thenReturn(OWNER_ID);
        loginUtil.when(LoginUtil::getRoleCodeList).thenReturn(List.of());
        loginUtil.when(LoginUtil::getRoleIdList).thenReturn(List.of(1L));
        AiConversation conversation = conversation(1L, OTHER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation);

        assertSame(conversation, service.getOwnedConversation(1L));
    }

    @Test
    @DisplayName("对话不存在应抛业务异常")
    void missingConversationShouldFail() {
        loginAs(OWNER_ID);
        when(baseMapper.selectById(1L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.getOwnedConversation(1L));
        assertEquals(Integer.valueOf(400), ex.getCode());
        assertEquals("对话不存在", ex.getMessage());
    }

    @Test
    @DisplayName("本人删除应同时清理消息与对话")
    void ownerCanDeleteConversationWithMessages() {
        loginAs(OWNER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation(1L, OWNER_ID));
        when(baseMapper.deleteById(1L)).thenReturn(1);

        service.deleteOwned(1L);

        verify(messageService).remove(any(Wrapper.class));
        verify(baseMapper).deleteById(1L);
    }

    @Test
    @DisplayName("非本人删除应被拒绝且不触发任何删除")
    void nonOwnerCannotDeleteConversation() {
        loginAs(OWNER_ID);
        when(baseMapper.selectById(1L)).thenReturn(conversation(1L, OTHER_ID));

        assertThrows(BusinessException.class, () -> service.deleteOwned(1L));

        verify(messageService, never()).remove(any(Wrapper.class));
        verify(baseMapper, never()).deleteById(any(java.io.Serializable.class));
    }
}
