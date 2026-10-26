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
package cn.jfspeed.common.util;

import cn.jfspeed.common.core.util.ULID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jffast
 */
class ULIDTest {

    @Nested
    @DisplayName("ULID 生成")
    class Generate {

        @Test
        @DisplayName("ulid() 应生成非空 ULID 对象")
        void shouldGenerateNonNullUlid() {
            ULID ulid = ULID.ulid();
            assertNotNull(ulid);
        }

        @Test
        @DisplayName("ulidStr() 应生成 26 位字符串")
        void shouldGenerate26CharString() {
            String ulidStr = ULID.ulidStr();
            assertNotNull(ulidStr);
            assertEquals(26, ulidStr.length());
        }

        @Test
        @DisplayName("ulidStr() 应只包含 Crockford Base32 字符")
        void shouldOnlyContainBase32Chars() {
            String validChars = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
            for (int i = 0; i < 100; i++) {
                String ulidStr = ULID.ulidStr();
                for (char c : ulidStr.toCharArray()) {
                    assertTrue(validChars.indexOf(c) >= 0,
                            "Invalid char: " + c + " in ULID: " + ulidStr);
                }
            }
        }

        @Test
        @DisplayName("时间戳应为正数且合理（毫秒级）")
        void shouldHaveReasonableTimestamp() {
            ULID ulid = ULID.ulid();
            long ts = ulid.getTimestamp();
            assertTrue(ts > 0);
            assertTrue(ts <= System.currentTimeMillis() + 1000);
        }
    }

    @Nested
    @DisplayName("ULID 解析")
    class Parse {

        @Test
        @DisplayName("fromString + toString 应可逆")
        void shouldRoundTrip() {
            ULID original = ULID.ulid();
            String str = original.toString();
            ULID parsed = ULID.fromString(str);
            assertEquals(original, parsed);
            assertEquals(str, parsed.toString());
        }

        @Test
        @DisplayName("fromString 传入 null 应抛异常")
        void shouldThrowOnNull() {
            assertThrows(IllegalArgumentException.class, () -> ULID.fromString(null));
        }

        @Test
        @DisplayName("fromString 传入长度不对应抛异常")
        void shouldThrowOnWrongLength() {
            assertThrows(IllegalArgumentException.class, () -> ULID.fromString("too-short"));
        }

        @Test
        @DisplayName("fromString 传入非法字符应抛异常")
        void shouldThrowOnInvalidChar() {
            assertThrows(IllegalArgumentException.class, () -> ULID.fromString("0123456789ABCDEFGHIJKLMNO"));
        }
    }

    @Nested
    @DisplayName("ULID 单调递增")
    class Monotonic {

        @Test
        @DisplayName("单调生成器应生成递增的 ULID")
        void shouldGenerateMonotonically() {
            ULID.MonotonicULIDSpi spi = ULID.monotonicUlid();
            ULID prev = spi.next();
            for (int i = 0; i < 100; i++) {
                ULID curr = spi.next();
                assertTrue(curr.compareTo(prev) > 0,
                        "Current ULID should be greater than previous");
                prev = curr;
            }
        }
    }

    @Nested
    @DisplayName("ULID 递增")
    class Increment {

        @Test
        @DisplayName("increment() 应生成比原值大的 ULID")
        void shouldIncrement() {
            ULID ulid = ULID.ulid();
            ULID incremented = ulid.increment();
            assertTrue(incremented.compareTo(ulid) > 0);
        }
    }

    @Nested
    @DisplayName("ULID 与 UUID 互转")
    class UuidConversion {

        @Test
        @DisplayName("ULID 与 UUID 应可逆转换")
        void shouldConvertToUuidAndBack() {
            ULID original = ULID.ulid();
            java.util.UUID uuid = original.toUUID();
            ULID converted = ULID.fromUUID(uuid);
            assertEquals(original, converted);
        }
    }
}
