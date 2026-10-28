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
package cn.jfspeed.social.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import cn.jfspeed.common.core.enums.AuthErrorCode;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.social.config.SocialProperties;
import cn.jfspeed.social.domain.SocialBind;
import cn.jfspeed.social.domain.SocialBindTicket;
import cn.jfspeed.social.domain.SocialLoginResult;
import cn.jfspeed.social.domain.SocialState;
import cn.jfspeed.social.domain.SocialUser;
import cn.jfspeed.social.mapper.SocialBindMapper;
import cn.jfspeed.social.provider.SocialLoginProvider;

/**
 * 社交账号绑定的归属契约回归测试。
 *
 * <p>覆盖两条「归属」红线：
 *
 * <ul>
 *   <li><b>回调不泄露他人 userId</b>：回调端点对任何登录用户可达。若该社交号已绑到他人账号，
 *       回吐其 userId 等于交给调用方一个可枚举的系统用户标识。回调只回 {@code bound}，
 *       归属判定留给 {@code /social/bind}。</li>
 *   <li><b>票据归属 fail-closed</b>：绑定票据必须带 userId 且等于当前登录用户。
 *       「userId 为 null 就跳过校验」会让一张不写 userId 的票据静默放行。</li>
 * </ul>
 *
 * @author jffast
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SocialLoginServiceBindContractTest {

    private static final String SECRET = "test-state-secret";

    @Mock
    private SocialBindMapper socialBindMapper;

    private SocialLoginService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 解析列名要 TableInfo；缺这步会在 wrapper 构造时 NPE，与业务无关。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SocialBind.class);
    }

    @BeforeEach
    void setUp() {
        SocialProperties properties = new SocialProperties();
        properties.setStateSecret(SECRET);
        SocialLoginProvider provider = new SocialLoginProvider() {
            @Override
            public String getProviderName() {
                return "github";
            }

            @Override
            public String getAuthorizationUrl(String state) {
                return "https://example.invalid/authorize";
            }

            @Override
            public String getAccessToken(String code) {
                return "access-token";
            }

            @Override
            public SocialUser getUserInfo(String accessToken) {
                SocialUser user = new SocialUser();
                user.setProvider("github");
                user.setOpenId("open-1");
                user.setUsername("octocat");
                return user;
            }
        };
        service = new SocialLoginService(properties, socialBindMapper, java.util.List.of(provider));
    }

    private static SocialState signedState(String provider) {
        SocialState state = new SocialState();
        state.setProvider(provider);
        state.setTargetUri("/dashboard");
        state.setExpiresAt(System.currentTimeMillis() + 60_000L);
        return state;
    }

    private static SocialBind bind(long userId) {
        SocialBind bind = new SocialBind();
        bind.setId(1L);
        bind.setUserId(userId);
        bind.setProvider("github");
        bind.setOpenId("open-1");
        return bind;
    }

    private static LoginUser loginUser(long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUserName("u" + id);
        return user;
    }

    @Test
    @DisplayName("已登录 + 该社交号已绑他人：回调只回 bound=true，不回吐他人 userId")
    void callbackDoesNotLeakOtherUsersId() {
        when(socialBindMapper.selectOne(any())).thenReturn(bind(4242L));

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginUserId).thenReturn(7L);

            SocialLoginResult result = service.callback("code-1",
                    signedState("github").encode(SECRET));

            assertNotNull(result);
            assertTrue(result.getBound(), "该社交号已绑定，bound 必须为 true");
            assertNull(result.getUserId(), "回调不得回吐他人 userId（可枚举的系统用户标识）");
            assertNotNull(result.getBindTicket(), "绑定票据仍要签发，绑定动作走 /social/bind");
        }
    }

    @Test
    @DisplayName("已登录 + 该社交号未绑定：bound=false，且同样不回 userId")
    void callbackWithoutExistingBind() {
        when(socialBindMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginUserId).thenReturn(7L);

            SocialLoginResult result = service.callback("code-1",
                    signedState("github").encode(SECRET));

            assertFalse(result.getBound());
            assertNull(result.getUserId());
        }
    }

    @Test
    @DisplayName("绑定票据缺 userId：直接拒绝（fail-closed，不静默放行）")
    void bindRejectsTicketWithoutUserId() {
        SocialBindTicket ticket = new SocialBindTicket();
        ticket.setProvider("github");
        ticket.setOpenId("open-1");
        ticket.setExpiresAt(System.currentTimeMillis() + 60_000L);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUser(7L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.bind(ticket.encode(SECRET)));

            assertEquals(AuthErrorCode.FORBIDDEN.getCode(), ex.getCode());
            verify(socialBindMapper, never()).insert(any(SocialBind.class));
        }
    }

    @Test
    @DisplayName("绑定票据的 userId 与当前登录用户不一致：拒绝")
    void bindRejectsTicketOfAnotherUser() {
        SocialBindTicket ticket = new SocialBindTicket();
        ticket.setProvider("github");
        ticket.setOpenId("open-1");
        ticket.setUserId(999L);
        ticket.setExpiresAt(System.currentTimeMillis() + 60_000L);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUser(7L));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.bind(ticket.encode(SECRET)));

            assertEquals(AuthErrorCode.FORBIDDEN.getCode(), ex.getCode());
            verify(socialBindMapper, never()).insert(any(SocialBind.class));
        }
    }

    @Test
    @DisplayName("绑定票据归属当前登录用户：写入绑定行")
    void bindAcceptsTicketOfCurrentUser() {
        SocialBindTicket ticket = new SocialBindTicket();
        ticket.setProvider("github");
        ticket.setOpenId("open-1");
        ticket.setUserId(7L);
        ticket.setExpiresAt(System.currentTimeMillis() + 60_000L);
        when(socialBindMapper.selectOne(any())).thenReturn(null);

        try (MockedStatic<LoginUtil> login = mockStatic(LoginUtil.class)) {
            login.when(LoginUtil::getLoginUser).thenReturn(loginUser(7L));

            service.bind(ticket.encode(SECRET));

            verify(socialBindMapper).insert(any(SocialBind.class));
        }
    }
}
