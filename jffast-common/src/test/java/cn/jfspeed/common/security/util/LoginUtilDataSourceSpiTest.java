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

import cn.hutool.extra.spring.SpringUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.security.spi.LoginDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LoginUtil 对 {@link LoginDataSource} SPI 两条路径的回归测试。
 *
 * <p>R-11：LoginUtil 历史上对 system 三服务（SysRoleMenuService/SysMenuService/SysUserService）
 * 用 {@code Class.forName + invoke} 反射跨层调用，签名漂移只能运行期暴露。SPI 化后必须钉住两条路径：
 * <ul>
 *     <li><b>无实现降级</b>（纯 common 栈未装 system）：warn + 返回空，不抛异常——与历史上的
 *     {@code ClassNotFoundException} 分支等价；</li>
 *     <li><b>实现存在走接口</b>：菜单 ID / 超管权限全集 / 按角色按钮权限按 SPI 方法映射路由。</li>
 * </ul>
 *
 * <p>测试上下文只注册 {@link SpringUtil}（hutool 静态上下文）；RedisUtil 不在 common 测试类路径上，
 * 缓存读写自动降级为「不缓存」，不影响断言。传显式角色列表以避开 Sa-Token 会话路径。
 *
 * @author jffast
 */
class LoginUtilDataSourceSpiTest {

    /** 记录调用轨迹的假实现，用于断言「实现存在走接口」路径的方法映射 */
    static class RecordingLoginDataSource implements LoginDataSource {
        final List<Long> menuIdCalls = new ArrayList<>();
        final List<Long> rolePermCalls = new ArrayList<>();
        final List<String> superPermCalls = new ArrayList<>();

        @Override
        public List<Long> listAllMenuIdList(Long roleId) {
            menuIdCalls.add(roleId);
            return List.of(11L, 12L);
        }

        @Override
        public List<String> listAllBtnPerm() {
            superPermCalls.add("super");
            return List.of("sys:user:list");
        }

        @Override
        public List<String> listAllBtnPerm(Long roleId) {
            rolePermCalls.add(roleId);
            return List.of("sys:role:list");
        }

        @Override
        public Object getFreshUser(Serializable id) {
            return null;
        }
    }

    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.refresh();
        new SpringUtil().setApplicationContext(context);
    }

    @AfterEach
    void tearDown() {
        new SpringUtil().setApplicationContext(null);
        context.close();
    }

    private void registerFake(RecordingLoginDataSource fake) {
        context.getBeanFactory().registerSingleton("recordingLoginDataSource", fake);
    }

    @Test
    @DisplayName("无 SPI 实现（纯 common 栈）：降级为返回空，不抛异常")
    void noImplDegradesToEmpty() {
        assertTrue(LoginUtil.getAllMenuIdList(List.of(5L)).isEmpty(),
                "无实现时菜单 ID 应降级为空列表");
        assertTrue(LoginUtil.getPermList(List.of(5L)).isEmpty(),
                "无实现时按角色权限应降级为空列表");
        assertTrue(LoginUtil.getPermList(List.of(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG)).isEmpty(),
                "无实现时超管权限全集应降级为空列表");
    }

    @Test
    @DisplayName("SPI 实现存在：菜单 ID 走接口并带回实现返回值")
    void implPresentRoutesMenuIdListThroughInterface() {
        RecordingLoginDataSource fake = new RecordingLoginDataSource();
        registerFake(fake);

        List<Long> menuIds = LoginUtil.getAllMenuIdList(List.of(5L));
        assertEquals(List.of(11L, 12L), menuIds, "菜单 ID 必须经 SPI 接口取自实现");
        assertEquals(List.of(5L), fake.menuIdCalls, "SPI 方法应收到角色 ID 5");
    }

    @Test
    @DisplayName("SPI 实现存在：普通角色走按角色按钮权限接口")
    void implPresentRoutesRolePermThroughInterface() {
        RecordingLoginDataSource fake = new RecordingLoginDataSource();
        registerFake(fake);

        List<String> perms = LoginUtil.getPermList(List.of(7L));
        assertEquals(List.of("sys:role:list"), perms);
        assertEquals(List.of(7L), fake.rolePermCalls);
        assertTrue(fake.superPermCalls.isEmpty(), "非超管角色不得触发超管权限全集接口");
    }

    @Test
    @DisplayName("SPI 实现存在：超管角色走超管权限全集接口")
    void implPresentRoutesSuperAdminPermThroughInterface() {
        RecordingLoginDataSource fake = new RecordingLoginDataSource();
        registerFake(fake);

        List<String> perms = LoginUtil.getPermList(List.of(BaseConstant.ROLE_ID_SUPER_ADMIN_LONG));
        assertEquals(List.of("sys:user:list"), perms);
        assertEquals(1, fake.superPermCalls.size(), "超管应走无参超管权限全集接口");
        assertTrue(fake.rolePermCalls.isEmpty(), "超管不得走按角色按钮权限接口");
    }
}
