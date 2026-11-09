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
package cn.jfspeed.common.security.util;

import cn.jfspeed.common.core.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件签名票据编解码用例（AC-05 / AC-06 / AC-18）。
 *
 * <p>票据是匿名直显的唯一凭据，因此它的三条性质必须被钉死：
 * <ol>
 *   <li>过期的票据必须失效（AC-05）；</li>
 *   <li>被篡改的票据必须失效，且失败提示与「记录不存在」完全一致（AC-06，防枚举）；</li>
 *   <li>票据必须绑定租户，跨租户复用在控制器层被拒（AC-18）。</li>
 * </ol>
 *
 * @author jffast
 */
class FileUrlSignUtilTest {

    private static final String SECRET = "unit-test-secret-for-file-url-sign";

    /** 19 位雪花 ID：超出 JS 安全整数（2^53-1），序列化必须是字符串 */
    private static final long BIG_FILE_ID = 1937468592845169665L;

    @Test
    @DisplayName("签发后能验回：文件 ID、租户、过期时间一致")
    void signAndVerifyRoundTrip() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;

        String ticket = FileUrlSignUtil.sign(BIG_FILE_ID, 7L, expireAt, SECRET);
        FileUrlSignUtil.FileUrlTicket parsed = FileUrlSignUtil.verify(ticket, SECRET);

        assertEquals(BIG_FILE_ID, parsed.fileId());
        assertEquals(7L, parsed.tenantId());
        assertEquals(expireAt, parsed.expireAt());
    }

    @Test
    @DisplayName("payload 里的文件 ID 是字符串：雪花 ID 不丢精度")
    void payloadKeepsFileIdAsString() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;

        String ticket = FileUrlSignUtil.sign(BIG_FILE_ID, 0L, expireAt, SECRET);
        String payload = FileUrlSignUtil.verifyAndGetPayload(ticket, SECRET);

        assertTrue(payload.contains("\"id\":\"" + BIG_FILE_ID + "\""),
                "id 必须是字符串形态，实际 payload：" + payload);
    }

    @Test
    @DisplayName("票据过期：验签失败，提示与「记录不存在」一致")
    void expiredTicketIsRejected() {
        long expiredAt = System.currentTimeMillis() / 1000L - 1L;
        String ticket = FileUrlSignUtil.sign(BIG_FILE_ID, 0L, expiredAt, SECRET);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileUrlSignUtil.verify(ticket, SECRET));

        assertEquals(FileUrlSignUtil.INVALID_MESSAGE, ex.getMessage());
        assertEquals(400, ex.getCode(), "失败口径必须是 HTTP 200 + 业务码 400");
    }

    @Test
    @DisplayName("篡改 payload：验签失败（签名覆盖 body 本身）")
    void tamperedPayloadIsRejected() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;
        String ticket = FileUrlSignUtil.sign(BIG_FILE_ID, 0L, expireAt, SECRET);
        int dot = ticket.lastIndexOf('.');
        String tampered = ticket.substring(0, dot) + "x" + ticket.substring(dot);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileUrlSignUtil.verify(tampered, SECRET));

        assertEquals(FileUrlSignUtil.INVALID_MESSAGE, ex.getMessage());
    }

    @Test
    @DisplayName("换一把密钥：验签失败（多实例密钥不一致必须表现为失败而不是放行）")
    void differentSecretIsRejected() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;
        String ticket = FileUrlSignUtil.sign(BIG_FILE_ID, 0L, expireAt, SECRET);

        assertThrows(BusinessException.class,
                () -> FileUrlSignUtil.verify(ticket, "another-secret"));
    }

    @Test
    @DisplayName("空票据 / 无分隔符 / 只有分隔符：一律拒绝")
    void malformedTicketsAreRejected() {
        assertThrows(BusinessException.class, () -> FileUrlSignUtil.verify(null, SECRET));
        assertThrows(BusinessException.class, () -> FileUrlSignUtil.verify("  ", SECRET));
        assertThrows(BusinessException.class, () -> FileUrlSignUtil.verify("no-dot-here", SECRET));
        assertThrows(BusinessException.class, () -> FileUrlSignUtil.verify(".", SECRET));
        assertThrows(BusinessException.class, () -> FileUrlSignUtil.verify("abc.", SECRET));
    }

    @Test
    @DisplayName("票据绑定租户：换租户签发的票据带着不同的 tid")
    void ticketBindsTenant() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;

        String t1 = FileUrlSignUtil.sign(BIG_FILE_ID, 1L, expireAt, SECRET);
        String t2 = FileUrlSignUtil.sign(BIG_FILE_ID, 2L, expireAt, SECRET);

        assertEquals(1L, FileUrlSignUtil.verify(t1, SECRET).tenantId());
        assertEquals(2L, FileUrlSignUtil.verify(t2, SECRET).tenantId());
        assertNotEquals(t1, t2, "租户不同 ⇒ 票据必须不同");
    }

    @Test
    @DisplayName("未配置密钥：拒绝签发（不能静默用空密钥签出可伪造的票据）")
    void blankSecretIsRejected() {
        long expireAt = System.currentTimeMillis() / 1000L + 900L;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> FileUrlSignUtil.sign(BIG_FILE_ID, 0L, expireAt, "  "));

        assertEquals("文件签名密钥未配置", ex.getMessage());
    }
}
