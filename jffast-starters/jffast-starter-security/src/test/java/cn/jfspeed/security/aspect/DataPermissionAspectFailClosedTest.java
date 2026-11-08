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
package cn.jfspeed.security.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mockito.MockedStatic;

import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.domain.dto.LoginUser;
import cn.jfspeed.common.security.util.LoginUtil;
import cn.jfspeed.mybatis.config.DataScopeContext;
import cn.jfspeed.security.annotation.DataPermission;

/**
 * 数据权限切面的三态语义：有登录态按注解计算；无登录态但有「已解析」标记时沿用提交线程
 * 的判定；两者都没有则一律拒绝（{@code 1 = 0}），不再退回全量。
 *
 * @author jffast
 */
class DataPermissionAspectFailClosedTest {

    public static class Target {
        @DataPermission(deptField = "d.id", userField = "u.id")
        public void query() {
        }
    }

    private MockedStatic<LoginUtil> login;

    @BeforeEach
    void setUp() {
        login = mockStatic(LoginUtil.class);
    }

    @AfterEach
    void tearDown() {
        login.close();
        DataScopeContext.clear();
    }

    private static DataPermission annotation() throws NoSuchMethodException {
        Method method = Target.class.getMethod("query");
        return method.getAnnotation(DataPermission.class);
    }

    private static ProceedingJoinPoint pointObserving(AtomicReference<String> observed) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        when(point.proceed()).thenAnswer(invocation -> {
            observed.set(DataScopeContext.get());
            return null;
        });
        return point;
    }

    @Test
    @DisplayName("无登录态 + 无已解析标记 ⇒ 1 = 0（fail-closed），执行完不残留")
    void deniesWithoutLoginAndWithoutResolvedMarker() throws Throwable {
        login.when(LoginUtil::getLoginUser).thenReturn(null);
        AtomicReference<String> observed = new AtomicReference<>("unset");

        new DataPermissionAspect().around(pointObserving(observed), annotation());

        assertEquals("1 = 0", observed.get());
        assertNull(DataScopeContext.get());
        assertFalse(DataScopeContext.isResolved());
    }

    @Test
    @DisplayName("无登录态 + 已解析（超管异步导出快照为 null）⇒ 保持不受限，不写成 1 = 0")
    void keepsUnrestrictedWhenSnapshotAlreadyResolved() throws Throwable {
        login.when(LoginUtil::getLoginUser).thenReturn(null);
        DataScopeContext.markResolved();
        AtomicReference<String> observed = new AtomicReference<>("unset");

        new DataPermissionAspect().around(pointObserving(observed), annotation());

        assertNull(observed.get());
        // 任务线程的已解析标记由任务包装器负责，切面退出后不得清掉
        assertTrue(DataScopeContext.isResolved());
    }

    @Test
    @DisplayName("无登录态 + 已解析 + 有快照片段 ⇒ 沿用快照，不重算")
    void keepsSnapshotWhenAlreadyResolved() throws Throwable {
        login.when(LoginUtil::getLoginUser).thenReturn(null);
        DataScopeContext.set("d.id = '12'");
        DataScopeContext.markResolved();
        AtomicReference<String> observed = new AtomicReference<>("unset");

        new DataPermissionAspect().around(pointObserving(observed), annotation());

        assertEquals("d.id = '12'", observed.get());
    }

    @Test
    @DisplayName("有登录态 ⇒ 按注解计算片段")
    void computesFromLoginUser() throws Throwable {
        LoginUser user = new LoginUser();
        user.setId(7L);
        Map<String, Object> dataScope = new HashMap<>();
        dataScope.put(BaseConstant.DATA_SCOPE_DEPT_ID, 12);
        dataScope.put(BaseConstant.DATA_SCOPE_SELF, 7L);
        user.setDataScope(dataScope);
        login.when(LoginUtil::getLoginUser).thenReturn(user);
        AtomicReference<String> observed = new AtomicReference<>("unset");

        new DataPermissionAspect().around(pointObserving(observed), annotation());

        assertEquals("d.id = '12' and u.id = 7", observed.get());
    }
}
