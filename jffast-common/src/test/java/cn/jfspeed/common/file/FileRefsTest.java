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
package cn.jfspeed.common.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 稳定引用 {@code file:{id}} 的编解码用例（AC-19）。
 *
 * <p>库里只允许存 {@code file:{id}}：签名 URL 有 TTL（落库即过期），
 * 裸存储路径会随 {@code access-path} 变化而失效且外泄即永久可读。
 *
 * @author jffast
 */
class FileRefsTest {

    @Test
    @DisplayName("构造与解析：file:{id} 往返一致")
    void ofAndParseRoundTrip() {
        assertEquals("file:123", FileRefs.of(123L));
        assertEquals(123L, FileRefs.parseId("file:123"));
        assertTrue(FileRefs.isRef("file:123"));
    }

    @Test
    @DisplayName("空 ID 构造出 null（降级态没有登记记录）")
    void ofNullReturnsNull() {
        assertNull(FileRefs.of(null));
    }

    @Test
    @DisplayName("非本形态（历史裸 URL / 空 / 非法数字）一律解析为 null，不做猜测式改写")
    void nonRefValuesReturnNull() {
        assertNull(FileRefs.parseId(null));
        assertNull(FileRefs.parseId(""));
        assertNull(FileRefs.parseId("   "));
        assertNull(FileRefs.parseId("/upload/2026/11/a.png"));
        assertNull(FileRefs.parseId("file:"));
        assertNull(FileRefs.parseId("file:abc"));
        assertFalse(FileRefs.isRef("/upload/2026/11/a.png"));
    }

    @Test
    @DisplayName("签名直显地址归一成稳定引用")
    void viewUrlNormalizesToStableRef() {
        assertEquals("file:456", FileRefs.toStableRef("/sys/file/view/456?ticket=abc.def"));
        assertEquals("file:456", FileRefs.toStableRef("/api/sys/file/view/456?ticket=abc.def"));
        assertEquals("file:456", FileRefs.toStableRef("file:456"));
    }

    @Test
    @DisplayName("归一化不了的旧值原样返回（交给迁移脚本处理，不猜）")
    void unnormalizableValuesStayAsIs() {
        assertEquals("/upload/2026/11/a.png", FileRefs.toStableRef("/upload/2026/11/a.png"));
        assertEquals("abc", FileRefs.toStableRef("abc"));
        assertNull(FileRefs.toStableRef(null));
    }

    @Test
    @DisplayName("从直显地址取 ID：带 query / fragment / 尾斜杠都能取到")
    void parseViewUrlIdHandlesVariants() {
        assertEquals(456L, FileRefs.parseViewUrlId("/sys/file/view/456?ticket=x"));
        assertEquals(456L, FileRefs.parseViewUrlId("/sys/file/view/456#frag"));
        assertEquals(456L, FileRefs.parseViewUrlId("/sys/file/view/456/"));
        assertNull(FileRefs.parseViewUrlId("/sys/file/view/abc"));
        assertNull(FileRefs.parseViewUrlId("/sys/file/view/"));
        assertNull(FileRefs.parseViewUrlId("/sys/file/view/1/2"));
        assertNull(FileRefs.parseViewUrlId("/upload/456"));
        assertNull(FileRefs.parseViewUrlId(null));
    }
}
