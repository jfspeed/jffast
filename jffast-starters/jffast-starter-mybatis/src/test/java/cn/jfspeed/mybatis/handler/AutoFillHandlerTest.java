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
package cn.jfspeed.mybatis.handler;

import cn.jfspeed.common.core.annotation.IgnoreBaseField;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link AutoFillHandler} 单元测试。
 * <p>
 * 测试环境无 Sa-Token 登录态，{@code LoginUtil.getLoginUserId()} 抛异常被
 * {@code catch (Throwable)} 兜底为 0L —— 与「未接入 Sa-Token 的服务」运行时行为一致，
 * 同时验证该兜底路径本身（2026-09-24 评审：只 catch(Exception) 会漏 NoClassDefFoundError）。
 *
 * @author jffast
 */
class AutoFillHandlerTest {

    private final AutoFillHandler handler = new AutoFillHandler();

    public static class FillEntity {
        private Long createBy;
        private LocalDateTime createTime;
        private Long updateBy;
        private LocalDateTime updateTime;

        public Long getCreateBy() {
            return createBy;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public Long getUpdateBy() {
            return updateBy;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }
    }

    @IgnoreBaseField(createBy = true, createTime = true)
    public static class IgnoredCreateEntity {
        private Long createBy;
        private LocalDateTime createTime;
        private Long updateBy;
        private LocalDateTime updateTime;

        public Long getCreateBy() {
            return createBy;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public Long getUpdateBy() {
            return updateBy;
        }

        public LocalDateTime getUpdateTime() {
            return updateTime;
        }
    }

    @Nested
    @DisplayName("insertFill")
    class InsertFill {

        @Test
        @DisplayName("四个基础字段全部填充（用户信息兜底 0L）")
        void fillsAllBaseFields() {
            FillEntity entity = new FillEntity();

            handler.insertFill(SystemMetaObject.forObject(entity));

            assertEquals(0L, entity.getCreateBy());
            assertEquals(0L, entity.getUpdateBy());
            assertNotNull(entity.getCreateTime());
            assertNotNull(entity.getUpdateTime());
        }

        @Test
        @DisplayName("已有值不被覆盖")
        void doesNotOverwriteExistingValues() {
            FillEntity entity = new FillEntity();
            org.apache.ibatis.reflection.MetaObject mo = SystemMetaObject.forObject(entity);
            mo.setValue("createBy", 99L);
            LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
            mo.setValue("createTime", fixed);

            handler.insertFill(mo);

            assertEquals(99L, entity.getCreateBy());
            assertEquals(fixed, entity.getCreateTime());
        }

        @Test
        @DisplayName("@IgnoreBaseField 标注的字段跳过填充")
        void skipsIgnoredFields() {
            IgnoredCreateEntity entity = new IgnoredCreateEntity();

            handler.insertFill(SystemMetaObject.forObject(entity));

            assertNull(entity.getCreateBy());
            assertNull(entity.getCreateTime());
            assertEquals(0L, entity.getUpdateBy());
            assertNotNull(entity.getUpdateTime());
        }
    }

    @Nested
    @DisplayName("updateFill")
    class UpdateFill {

        @Test
        @DisplayName("只填充 updateBy / updateTime，不碰 create 字段")
        void fillsOnlyUpdateFields() {
            FillEntity entity = new FillEntity();

            handler.updateFill(SystemMetaObject.forObject(entity));

            assertEquals(0L, entity.getUpdateBy());
            assertNotNull(entity.getUpdateTime());
            assertNull(entity.getCreateBy());
            assertNull(entity.getCreateTime());
        }
    }
}
