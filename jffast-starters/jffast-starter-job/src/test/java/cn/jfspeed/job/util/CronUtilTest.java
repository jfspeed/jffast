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
package cn.jfspeed.job.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRON 表达式工具单元测试。
 *
 * @author jffast
 */
class CronUtilTest {

    @Test
    @DisplayName("合法表达式 isValid 为 true")
    void validExpressions() {
        assertTrue(CronUtil.isValid("0 0 2 * * ?"));
        assertTrue(CronUtil.isValid("0 0/30 * * * ?"));
        assertTrue(CronUtil.isValid("0 0 12 ? * MON-FRI"));
    }

    @Test
    @DisplayName("非法表达式 isValid 为 false（不抛异常）")
    void invalidExpressions() {
        assertFalse(CronUtil.isValid("not-a-cron"));
        assertFalse(CronUtil.isValid("0 0 2 * *"));
        assertFalse(CronUtil.isValid(""));
    }

    @Test
    @DisplayName("validate：合法不抛，非法抛 IllegalArgumentException 且消息携带表达式")
    void validate() {
        CronUtil.validate("0 0/5 * * * ?");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CronUtil.validate("oops"));
        assertTrue(ex.getMessage().contains("oops"));
    }

    @Test
    @DisplayName("getNextExecution 返回晚于当前的下次执行时间；非法表达式返回 null")
    void nextExecution() {
        Date now = new Date();
        Date next = CronUtil.getNextExecution("0 0 2 * * ?");

        assertNotNull(next);
        assertFalse(next.before(now));
        assertNull(CronUtil.getNextExecution("bad"));
    }
}
