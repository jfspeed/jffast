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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 社交账号绑定回跳地址白名单的回归测试。
 *
 * <p>要防的是**开放重定向**：`targetUri` 由客户端传入，服务端只是给它签名。签名防的是"被改"，
 * 防不了"一开始就填了别的地址" —— 攻击者可以让服务端替自己签一个 {@code https://evil.com}，
 * 回调时原样回显，前端再据此跳转。所以白名单必须在**入参侧**就落地。
 */
class SocialLoginServiceTargetUriTest {

    @ParameterizedTest
    @DisplayName("站内相对路径：放行")
    @ValueSource(strings = {
            "/",
            "/dashboard",
            "/dashboard/base/index",
            "/user/profile?tab=info",
            "/system/user?page=1&size=20"
    })
    void allowsRelativePathsWithinSite(String uri) {
        assertEquals(uri, SocialLoginService.sanitizeTargetUri(uri));
    }

    @ParameterizedTest
    @DisplayName("绝对地址 / 协议相对地址 / 跨站跳转：一律拒绝")
    @ValueSource(strings = {
            "https://evil.com",
            "http://evil.com/path",
            "//evil.com/path",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "ftp://evil.com",
            "\\\\evil.com",
            "/\\evil.com"
    })
    void rejectsAbsoluteAndProtocolRelativeUrls(String uri) {
        assertNull(SocialLoginService.sanitizeTargetUri(uri), "必须拒绝：" + uri);
    }

    @ParameterizedTest
    @DisplayName("路径穿越 / 反斜杠：一律拒绝")
    @ValueSource(strings = {
            "/../admin",
            "/a/../../etc/passwd",
            "/a/..",
            "/path\\to",
            "/path/with\\backslash"
    })
    void rejectsTraversalAndBackslash(String uri) {
        assertNull(SocialLoginService.sanitizeTargetUri(uri), "必须拒绝：" + uri);
    }

    @Test
    @DisplayName("查询串里的普通冒号不是协议分隔符，允许")
    void allowsColonInsideQuery() {
        // 危险的是 "://"（带 scheme）或开头的 "//"（协议相对）；路径中间的冒号不是跳转向量
        assertEquals("/search?q=a:b", SocialLoginService.sanitizeTargetUri("/search?q=a:b"));
    }

    @Test
    @DisplayName("空值 / 空白：视为没有回跳地址")
    void rejectsBlank() {
        assertNull(SocialLoginService.sanitizeTargetUri(null));
        assertNull(SocialLoginService.sanitizeTargetUri(""));
        assertNull(SocialLoginService.sanitizeTargetUri("   "));
    }

    @Test
    @DisplayName("控制字符：拒绝")
    void rejectsControlCharacters() {
        assertNull(SocialLoginService.sanitizeTargetUri("/path\nSet-Cookie: x=1"));
        assertNull(SocialLoginService.sanitizeTargetUri("/path\r\nLocation: https://evil.com"));
        assertNull(SocialLoginService.sanitizeTargetUri("/path\u0000"));
    }

    @Test
    @DisplayName("首尾空白会被裁掉后再判定")
    void trimsBeforeValidation() {
        assertEquals("/dashboard", SocialLoginService.sanitizeTargetUri("  /dashboard  "));
        // 裁剪后仍不是站内相对路径 → 拒绝
        assertNull(SocialLoginService.sanitizeTargetUri("  https://evil.com  "));
    }
}
