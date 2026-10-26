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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.common.core.annotation.SensitiveMask;
import cn.jfspeed.common.core.util.SensitiveMaskUtil;

/**
 * @author jffast
 */
@DisplayName("SensitiveMaskUtil 脱敏工具测试")
class SensitiveMaskUtilTest {

    @Test
    @DisplayName("文本正则脱敏：手机号")
    void shouldMaskMobile() {
        String text = "联系人手机：13812345678";
        assertEquals("联系人手机：138****5678", SensitiveMaskUtil.maskText(text));
    }

    @Test
    @DisplayName("文本正则脱敏：身份证号")
    void shouldMaskIdCard() {
        String text = "身份证：110101199001011234";
        assertEquals("身份证：110101********1234", SensitiveMaskUtil.maskText(text));
    }

    @Test
    @DisplayName("文本正则脱敏：邮箱")
    void shouldMaskEmail() {
        String text = "邮箱：admin@example.com";
        assertEquals("邮箱：ad****@example.com", SensitiveMaskUtil.maskText(text));
    }

    @Test
    @DisplayName("文本正则脱敏：密码键值对")
    void shouldMaskPassword() {
        String text = "password=123456, secretKey=abcd";
        assertEquals("password=\"****\", secretKey=\"****\"", SensitiveMaskUtil.maskText(text));
    }

    @Test
    @DisplayName("按类型脱敏：全部隐藏")
    void shouldMaskAll() {
        assertEquals("******", SensitiveMaskUtil.mask("123456", SensitiveMask.MaskType.ALL_MASK));
    }

    @Test
    @DisplayName("按类型脱敏：中间隐藏")
    void shouldMaskMiddle() {
        assertEquals("1****6", SensitiveMaskUtil.mask("123456", SensitiveMask.MaskType.MIDDLE_MASK));
    }

    @Test
    @DisplayName("对象递归脱敏：只处理带注解字段")
    void shouldMaskObjectFields() {
        User user = new User();
        user.setName("张三");
        user.setMobile("13812345678");
        SensitiveMaskUtil.mask(user);
        assertEquals("张三", user.getName());
        assertEquals("1*********8", user.getMobile());
    }

    @Test
    @DisplayName("空值安全")
    void shouldHandleNullAndEmpty() {
        assertNull(SensitiveMaskUtil.maskText(null));
        assertEquals("", SensitiveMaskUtil.maskText(""));
        SensitiveMaskUtil.mask(null);
    }

    static class User {
        private String name;

        @SensitiveMask(type = SensitiveMask.MaskType.MIDDLE_MASK, prefixKeep = 1, suffixKeep = 1)
        private String mobile;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMobile() {
            return mobile;
        }

        public void setMobile(String mobile) {
            this.mobile = mobile;
        }
    }
}
