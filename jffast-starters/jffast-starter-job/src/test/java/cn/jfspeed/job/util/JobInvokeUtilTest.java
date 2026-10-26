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

import com.example.fixture.ExternalJobBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时任务调用目标解析与白名单校验单元测试。
 *
 * <p>无 Spring 上下文：{@code SpringContextHolder.tryGetBean} 返回 null，
 * 白名单回退为空（只允许 {@code @JfFastJob} 声明的方法）。
 *
 * @author jffast
 */
class JobInvokeUtilTest {

    @Nested
    @DisplayName("调用目标格式校验")
    class TargetParsing {

        @Test
        @DisplayName("空 / null 调用目标被拒绝")
        void shouldRejectBlankTarget() {
            assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.invokeMethod(""));
            assertThrows(IllegalArgumentException.class, () -> JobInvokeUtil.invokeMethod(null));
        }

        @Test
        @DisplayName("缺少 bean. 分隔符的调用目标被拒绝")
        void shouldRejectTargetWithoutBeanSeparator() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> JobInvokeUtil.invokeMethod("demoJobService"));
            assertTrue(ex.getMessage().contains("格式错误"));
        }
    }

    @Nested
    @DisplayName("调用目标白名单（防任意方法提权）")
    class InvokeWhitelist {

        @Test
        @DisplayName("默认未配置允许包：允许包内的非注解方法也被拒绝")
        void shouldRejectUnannotatedMethodInDefaultPackage() throws Exception {
            Method method = SelfBean.class.getMethod("task");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> JobInvokeUtil.checkInvokeAllowed(new SelfBean(), method, "selfBean.task"));
            assertTrue(ex.getMessage().contains("白名单"));
        }

        @Test
        @DisplayName("非允许包且无 @JfFastJob 的方法被拒绝")
        void shouldRejectForeignMethod() throws Exception {
            // String#trim 由 java.lang 声明，不在允许包内，且无 @JfFastJob
            Method method = String.class.getMethod("trim");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> JobInvokeUtil.checkInvokeAllowed("abc", method, "str.trim"));
            assertTrue(ex.getMessage().contains("白名单"));
        }

        @Test
        @DisplayName("非允许包但标注 @JfFastJob 的方法放行")
        void shouldAllowAnnotatedMethodOutsidePackage() throws Exception {
            ExternalJobBean bean = new ExternalJobBean();
            Method method = ExternalJobBean.class.getMethod("annotated");
            assertDoesNotThrow(() -> JobInvokeUtil.checkInvokeAllowed(bean, method, "externalJobBean.annotated"));
        }

        @Test
        @DisplayName("非允许包且未标注 @JfFastJob 的方法仍被拒绝")
        void shouldRejectUnannotatedMethodOutsidePackage() throws Exception {
            ExternalJobBean bean = new ExternalJobBean();
            Method method = ExternalJobBean.class.getMethod("notAnnotated");
            assertThrows(IllegalArgumentException.class,
                    () -> JobInvokeUtil.checkInvokeAllowed(bean, method, "externalJobBean.notAnnotated"));
        }
    }

    /** 未标注 @JfFastJob 的示例 Bean（默认白名单为空，应被拒绝）。 */
    static class SelfBean {

        public void task() {
            // 测试替身，无逻辑
        }
    }
}
