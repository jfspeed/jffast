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
package cn.jfspeed.social.util;

import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.social.domain.SocialBindTicket;
import cn.jfspeed.social.domain.SocialState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth state / 绑定票据签名校验用例。
 *
 * @author jffast
 */
class SignedStateUtilTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    @DisplayName("签名后可原样取回 payload")
    void shouldRoundTripPayload() {
        String signed = SignedStateUtil.sign("{\"a\":1}", SECRET);
        assertEquals("{\"a\":1}", SignedStateUtil.verifyAndGetPayload(signed, SECRET));
    }

    @Test
    @DisplayName("换密钥验签应失败")
    void shouldRejectWrongSecret() {
        String signed = SignedStateUtil.sign("payload", SECRET);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SignedStateUtil.verifyAndGetPayload(signed, "another-secret"));
        assertEquals(Integer.valueOf(400), ex.getCode());
    }

    @Test
    @DisplayName("篡改签名应失败")
    void shouldRejectTamperedSignature() {
        String signed = SignedStateUtil.sign("payload", SECRET);
        String tampered = signed.substring(0, signed.lastIndexOf('.') + 1) + "deadbeef";
        assertThrows(BusinessException.class,
                () -> SignedStateUtil.verifyAndGetPayload(tampered, SECRET));
    }

    @Test
    @DisplayName("篡改 body 应失败")
    void shouldRejectTamperedBody() {
        String signed = SignedStateUtil.sign("payload", SECRET);
        String tampered = (signed.charAt(0) == 'A' ? "B" : "A") + signed.substring(1);
        assertThrows(BusinessException.class,
                () -> SignedStateUtil.verifyAndGetPayload(tampered, SECRET));
    }

    @Test
    @DisplayName("缺少签名或空值应失败")
    void shouldRejectUnsignedInput() {
        assertThrows(BusinessException.class, () -> SignedStateUtil.verifyAndGetPayload("", SECRET));
        assertThrows(BusinessException.class, () -> SignedStateUtil.verifyAndGetPayload("{\"a\":1}", SECRET));
        assertThrows(BusinessException.class, () -> SignedStateUtil.verifyAndGetPayload("abc.", SECRET));
    }

    @Test
    @DisplayName("未配置密钥应拒绝签发/验签")
    void shouldRejectBlankSecret() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SignedStateUtil.sign("payload", " "));
        assertEquals(Integer.valueOf(500), ex.getCode());
    }

    @Nested
    @DisplayName("SocialState")
    class SocialStateTest {

        @Test
        @DisplayName("有效 state 应解码出平台与回跳地址")
        void shouldDecodeValidState() {
            SocialState state = new SocialState();
            state.setProvider("github");
            state.setTargetUri("/dashboard");
            state.setRandom("r1");
            state.setExpiresAt(System.currentTimeMillis() + 60_000);

            SocialState decoded = SocialState.decode(state.encode(SECRET), SECRET);

            assertEquals("github", decoded.getProvider());
            assertEquals("/dashboard", decoded.getTargetUri());
            assertEquals("r1", decoded.getRandom());
        }

        @Test
        @DisplayName("过期 state 应被拒绝")
        void shouldRejectExpiredState() {
            SocialState state = new SocialState();
            state.setProvider("github");
            state.setExpiresAt(System.currentTimeMillis() - 1);

            String encoded = state.encode(SECRET);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SocialState.decode(encoded, SECRET));
            assertTrue(ex.getMessage().contains("已过期"));
        }

        @Test
        @DisplayName("缺少平台标识应被拒绝")
        void shouldRejectStateWithoutProvider() {
            SocialState state = new SocialState();
            state.setExpiresAt(System.currentTimeMillis() + 60_000);

            String encoded = state.encode(SECRET);
            assertThrows(BusinessException.class, () -> SocialState.decode(encoded, SECRET));
        }
    }

    @Nested
    @DisplayName("SocialBindTicket")
    class SocialBindTicketTest {

        @Test
        @DisplayName("有效票据应解码出社交用户与归属用户")
        void shouldDecodeValidTicket() {
            SocialBindTicket ticket = new SocialBindTicket();
            ticket.setProvider("github");
            ticket.setOpenId("open-1");
            ticket.setNickname("tester");
            ticket.setUserId(7L);
            ticket.setExpiresAt(System.currentTimeMillis() + 60_000);

            SocialBindTicket decoded = SocialBindTicket.decode(ticket.encode(SECRET), SECRET);

            assertEquals("github", decoded.getProvider());
            assertEquals("open-1", decoded.getOpenId());
            assertEquals(7L, decoded.getUserId());
            assertEquals("github", decoded.toSocialUser().getProvider());
            assertEquals("open-1", decoded.toSocialUser().getOpenId());
        }

        @Test
        @DisplayName("过期票据应被拒绝")
        void shouldRejectExpiredTicket() {
            SocialBindTicket ticket = new SocialBindTicket();
            ticket.setProvider("gitee");
            ticket.setOpenId("open-2");
            ticket.setExpiresAt(System.currentTimeMillis() - 1);

            String encoded = ticket.encode(SECRET);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> SocialBindTicket.decode(encoded, SECRET));
            assertTrue(ex.getMessage().contains("已过期"));
        }

        @Test
        @DisplayName("缺少 provider/openId 的票据应被拒绝")
        void shouldRejectIncompleteTicket() {
            SocialBindTicket ticket = new SocialBindTicket();
            ticket.setExpiresAt(System.currentTimeMillis() + 60_000);

            String encoded = ticket.encode(SECRET);
            assertThrows(BusinessException.class, () -> SocialBindTicket.decode(encoded, SECRET));
        }
    }
}
