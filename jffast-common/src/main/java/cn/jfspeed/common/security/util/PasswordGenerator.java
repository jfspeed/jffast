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

import java.security.SecureRandom;

/**
 * 口令生成工具。
 * <p>
 * 用途：当 {@code jffast.system.init-password} 未配置时，为「初始化 admin」、
 * 「重置用户密码」、「导入用户」等场景生成一次性强口令，而不是沿用某个固定密码。
 * <p>
 * 为什么必须这样：固定口令一旦随仓库公开（README、SQL seed、配置默认值），
 * 就是全网已知的凭据——任何按默认配置部署的实例都能被直接登录。
 * 改为运行时随机生成并打印到启动日志，则每个实例的口令互不相同且不可预测。
 * <p>
 * 生成的口令满足 {@code validatePasswordStrength} 的约束（8–20 位，含大小写字母、数字、特殊字符）。
 *
 * @author jffast
 */
public final class PasswordGenerator {

    /** 默认口令长度 */
    public static final int DEFAULT_LENGTH = 16;

    /**
     * 口令字符表：剔除易混淆字符（0/O、1/l/I）与 Shell 元字符（如 ` ' " \ $ 之外再排除 &lt; &gt; | 等），
     * 便于通过聊天工具传递而不被转义或看错。
     */
    private static final char[] CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*".toCharArray();

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SYMBOL = "!@#$%^&*";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /** 生成默认长度（16 位）的强口令 */
    public static String generate() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * 生成指定长度的强口令。
     * <p>
     * 先保证大写 / 小写 / 数字 / 特殊字符各至少出现一位，剩余位随机填充，
     * 最后整体 Fisher–Yates 打乱——否则前四位会呈现固定模式，降低有效熵。
     *
     * @param length 口令长度，不得小于 8
     */
    public static String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("口令长度不能小于 8");
        }

        char[] pwd = new char[length];
        pwd[0] = randomOf(UPPER);
        pwd[1] = randomOf(LOWER);
        pwd[2] = randomOf(DIGIT);
        pwd[3] = randomOf(SYMBOL);
        for (int i = 4; i < length; i++) {
            pwd[i] = CHARS[RANDOM.nextInt(CHARS.length)];
        }

        for (int i = pwd.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = pwd[i];
            pwd[i] = pwd[j];
            pwd[j] = tmp;
        }

        return new String(pwd);
    }

    private static char randomOf(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
