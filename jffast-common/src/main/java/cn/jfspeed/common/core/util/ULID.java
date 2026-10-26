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
package cn.jfspeed.common.core.util;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;

/**
 * @author jffast
 */
@Slf4j
public class ULID implements Serializable, Comparable<ULID> {

    private static final long serialVersionUID = -2938569386388233525L;

    private static final long TIMESTAMP_MASK = 0xffff000000000000L;
    private static final int RANDOMNESS_BYTE_LEN = 10;
    private static final char[] DEFAULT_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C',
            'D', 'E', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X', 'Y', 'Z'};
    private static final int DEFAULT_ALPHABET_MASK = 0b11111;
    private static final int ULID_CHAR_LEN = 26;
    private static final long OVERFLOW = 0x0000000000000000L;

    // 反向解析映射表：字符 -> 对应索引值
    private static final byte[] ALPHABET_DECODE;

    static {
        ALPHABET_DECODE = new byte[128];
        for (int i = 0; i < ALPHABET_DECODE.length; i++) {
            ALPHABET_DECODE[i] = -1;
        }
        for (int i = 0; i < DEFAULT_ALPHABET.length; i++) {
            char c = DEFAULT_ALPHABET[i];
            ALPHABET_DECODE[c] = (byte) i;
            // 兼容小写
            ALPHABET_DECODE[Character.toLowerCase(c)] = (byte) i;
        }
    }

    private final long msb;
    private final long lsb;

    public ULID(long msb, long lsb) {
        this.msb = msb;
        this.lsb = lsb;
    }

    public ULID(long timestamp, byte[] randomness) {
        if ((timestamp & TIMESTAMP_MASK) != 0) {
            throw new IllegalArgumentException("Invalid timestamp");
        }
        if (Objects.isNull(randomness) || RANDOMNESS_BYTE_LEN != randomness.length) {
            throw new IllegalArgumentException("Invalid randomness");
        }
        long msb = 0;
        long lsb = 0;
        msb |= timestamp << 16;
        msb |= (long) (randomness[0x0] & 0xff) << 8;
        msb |= randomness[0x1] & 0xff;
        lsb |= (long) (randomness[0x2] & 0xff) << 56;
        lsb |= (long) (randomness[0x3] & 0xff) << 48;
        lsb |= (long) (randomness[0x4] & 0xff) << 40;
        lsb |= (long) (randomness[0x5] & 0xff) << 32;
        lsb |= (long) (randomness[0x6] & 0xff) << 24;
        lsb |= (long) (randomness[0x7] & 0xff) << 16;
        lsb |= (long) (randomness[0x8] & 0xff) << 8;
        lsb |= (randomness[0x9] & 0xff);
        this.msb = msb;
        this.lsb = lsb;
    }

    public ULID(ULID other) {
        this.msb = other.msb;
        this.lsb = other.lsb;
    }

    /**
     * 从 ULID 字符串解析为 ULID 对象
     */
    public static ULID fromString(String str) {
        if (str == null || str.length() != ULID_CHAR_LEN) {
            throw new IllegalArgumentException("ULID string must be 26 characters long");
        }
        char[] chars = str.toCharArray();
        long timestamp = 0;
        long randMost = 0;
        long randLeast = 0;

        // 前10位：时间戳
        for (int i = 0; i < 10; i++) {
            byte val = ALPHABET_DECODE[chars[i]];
            if (val == -1) {
                throw new IllegalArgumentException("Invalid ULID character: " + chars[i]);
            }
            timestamp = (timestamp << 5) | val;
        }
        // 中间8位：高随机
        for (int i = 10; i < 18; i++) {
            byte val = ALPHABET_DECODE[chars[i]];
            if (val == -1) {
                throw new IllegalArgumentException("Invalid ULID character: " + chars[i]);
            }
            randMost = (randMost << 5) | val;
        }
        // 后8位：低随机
        for (int i = 18; i < 26; i++) {
            byte val = ALPHABET_DECODE[chars[i]];
            if (val == -1) {
                throw new IllegalArgumentException("Invalid ULID character: " + chars[i]);
            }
            randLeast = (randLeast << 5) | val;
        }

        long msb = (timestamp << 16) | ((randMost >>> 24) & 0xFFFFL);
        long lsb = (randMost << 40) | (randLeast & 0xFFFF_FFFFFFFFL);
        return new ULID(msb, lsb);
    }

    public static byte[] defaultRandomBytes(int len) {
        byte[] bytes = new byte[len];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }

    public static ULID ulid() {
        return ulid(System::currentTimeMillis, ULID::defaultRandomBytes);
    }

    public static String ulidStr() {
        return ulid(System::currentTimeMillis, ULID::defaultRandomBytes).toString();
    }

    public static ULID ulid(Supplier<Long> timestampProvider,
                            IntFunction<byte[]> randomnessProvider) {
        return new ULID(timestampProvider.get(), randomnessProvider.apply(RANDOMNESS_BYTE_LEN));
    }

    public static MonotonicULIDSpi monotonicUlid() {
        return monotonicUlid(System::currentTimeMillis, ULID::defaultRandomBytes);
    }

    public static MonotonicULIDSpi monotonicUlid(Supplier<Long> timestampProvider,
                                                 IntFunction<byte[]> randomnessProvider) {
        return new MonotonicULID(timestampProvider, randomnessProvider, timestampProvider.get(),
                randomnessProvider.apply(RANDOMNESS_BYTE_LEN));
    }

    public static ULID fromUUID(UUID uuid) {
        return new ULID(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits());
    }

    public UUID toUUID() {
        return new UUID(this.msb, this.lsb);
    }

    public long getMostSignificantBits() {
        return this.msb;
    }

    public long getLeastSignificantBits() {
        return this.lsb;
    }

    public long getTimestamp() {
        return this.msb >>> 16;
    }

    public byte[] getRandomness() {
        byte[] randomness = new byte[RANDOMNESS_BYTE_LEN];
        randomness[0x0] = (byte) (this.msb >>> 8);
        randomness[0x1] = (byte) this.msb;
        randomness[0x2] = (byte) (this.lsb >>> 56);
        randomness[0x3] = (byte) (this.lsb >>> 48);
        randomness[0x4] = (byte) (this.lsb >>> 40);
        randomness[0x5] = (byte) (this.lsb >>> 32);
        randomness[0x6] = (byte) (this.lsb >>> 24);
        randomness[0x7] = (byte) (this.lsb >>> 16);
        randomness[0x8] = (byte) (this.lsb >>> 8);
        randomness[0x9] = (byte) this.lsb;
        return randomness;
    }

    public ULID increment() {
        long newMsb = this.msb;
        long newLsb = this.lsb + 1;
        if (newLsb == OVERFLOW) {
            newMsb += 1;
        }
        return new ULID(newMsb, newLsb);
    }

    @Override
    public String toString() {
        return toCanonicalString(DEFAULT_ALPHABET);
    }

    public String toCanonicalString(char[] alphabet) {
        char[] chars = new char[ULID_CHAR_LEN];
        long timestamp = this.msb >> 16;
        long randMost = ((this.msb & 0xffffL) << 24) | (this.lsb >>> 40);
        long randLeast = (this.lsb & 0xffffffffffL);
        chars[0x00] = alphabet[(int) (timestamp >>> 45 & DEFAULT_ALPHABET_MASK)];
        chars[0x01] = alphabet[(int) (timestamp >>> 40 & DEFAULT_ALPHABET_MASK)];
        chars[0x02] = alphabet[(int) (timestamp >>> 35 & DEFAULT_ALPHABET_MASK)];
        chars[0x03] = alphabet[(int) (timestamp >>> 30 & DEFAULT_ALPHABET_MASK)];
        chars[0x04] = alphabet[(int) (timestamp >>> 25 & DEFAULT_ALPHABET_MASK)];
        chars[0x05] = alphabet[(int) (timestamp >>> 20 & DEFAULT_ALPHABET_MASK)];
        chars[0x06] = alphabet[(int) (timestamp >>> 15 & DEFAULT_ALPHABET_MASK)];
        chars[0x07] = alphabet[(int) (timestamp >>> 10 & DEFAULT_ALPHABET_MASK)];
        chars[0x08] = alphabet[(int) (timestamp >>> 5 & DEFAULT_ALPHABET_MASK)];
        chars[0x09] = alphabet[(int) (timestamp & DEFAULT_ALPHABET_MASK)];
        chars[0x0a] = alphabet[(int) (randMost >>> 35 & DEFAULT_ALPHABET_MASK)];
        chars[0x0b] = alphabet[(int) (randMost >>> 30 & DEFAULT_ALPHABET_MASK)];
        chars[0x0c] = alphabet[(int) (randMost >>> 25 & DEFAULT_ALPHABET_MASK)];
        chars[0x0d] = alphabet[(int) (randMost >>> 20 & DEFAULT_ALPHABET_MASK)];
        chars[0x0e] = alphabet[(int) (randMost >>> 15 & DEFAULT_ALPHABET_MASK)];
        chars[0x0f] = alphabet[(int) (randMost >>> 10 & DEFAULT_ALPHABET_MASK)];
        chars[0x10] = alphabet[(int) (randMost >>> 5 & DEFAULT_ALPHABET_MASK)];
        chars[0x11] = alphabet[(int) (randMost & DEFAULT_ALPHABET_MASK)];
        chars[0x12] = alphabet[(int) (randLeast >>> 35 & DEFAULT_ALPHABET_MASK)];
        chars[0x13] = alphabet[(int) (randLeast >>> 30 & DEFAULT_ALPHABET_MASK)];
        chars[0x14] = alphabet[(int) (randLeast >>> 25 & DEFAULT_ALPHABET_MASK)];
        chars[0x15] = alphabet[(int) (randLeast >>> 20 & DEFAULT_ALPHABET_MASK)];
        chars[0x16] = alphabet[(int) (randLeast >>> 15 & DEFAULT_ALPHABET_MASK)];
        chars[0x17] = alphabet[(int) (randLeast >>> 10 & DEFAULT_ALPHABET_MASK)];
        chars[0x18] = alphabet[(int) (randLeast >>> 5 & DEFAULT_ALPHABET_MASK)];
        chars[0x19] = alphabet[(int) (randLeast & DEFAULT_ALPHABET_MASK)];
        return new String(chars);
    }

    public String toCanonicalString0() {
        byte[] bytes = new byte[ULID_CHAR_LEN];
        formatUnsignedLong0(this.lsb & 0xffffffffffL, 5, bytes, 18, 8);
        formatUnsignedLong0(((this.msb & 0xffffL) << 24) | (this.lsb >>> 40), 5, bytes, 10, 8);
        formatUnsignedLong0(this.msb >> 16, 5, bytes, 0, 10);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void formatUnsignedLong0(long val, int shift, byte[] buf, int offset, int len) {
        int charPos = offset + len;
        long radix = 1L << shift;
        long mask = radix - 1;
        do {
            buf[--charPos] = (byte) DEFAULT_ALPHABET[(int) (val & mask)];
            val >>>= shift;
        } while (charPos > offset);
    }

    @Override
    public int compareTo(ULID o) {
        int mostSigBits = Long.compare(this.msb, o.msb);
        return mostSigBits != 0 ? mostSigBits : Long.compare(this.lsb, o.lsb);
    }

    @Override
    public boolean equals(Object obj) {
        if ((Objects.isNull(obj)) || (obj.getClass() != ULID.class)) {
            return false;
        }
        ULID id = (ULID) obj;
        return (this.msb == id.msb && this.lsb == id.lsb);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.msb ^ this.lsb);
    }

    public interface MonotonicULIDSpi {
        ULID next();
    }

    private static class MonotonicULID extends ULID implements MonotonicULIDSpi {

        private static final long serialVersionUID = -9158161806889605101L;

        private volatile ULID lastULID;
        private final Supplier<Long> timestampProvider;
        private final IntFunction<byte[]> randomnessProvider;

        public MonotonicULID(Supplier<Long> timestampProvider,
                             IntFunction<byte[]> randomnessProvider,
                             long timestamp,
                             byte[] randomness) {
            super(timestamp, randomness);
            this.timestampProvider = timestampProvider;
            this.randomnessProvider = randomnessProvider;
            this.lastULID = new ULID(timestamp, randomness);
        }

        public ULID increment() {
            long newMsb = lastULID.msb;
            long newLsb = lastULID.lsb + 1;
            if (newLsb == OVERFLOW) {
                newMsb += 1;
            }
            return new ULID(newMsb, newLsb);
        }

        @Override
        public synchronized ULID next() {
            long lastTimestamp = lastULID.getTimestamp();
            // 修复：取最新时间戳，不是旧对象时间
            long timestamp = timestampProvider.get();
            if (lastTimestamp >= timestamp || timestamp - lastTimestamp <= 1000) {
                this.lastULID = this.increment();
            } else {
                this.lastULID = new ULID(timestampProvider.get(), randomnessProvider.apply(RANDOMNESS_BYTE_LEN));
            }
            return new ULID(this.lastULID);
        }
    }
}