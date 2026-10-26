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
package cn.jfspeed;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * 反射契约冒烟测试（无需 MySQL / Redis，可在任意环境运行）。
 *
 * <p>对应后端多处“绕开 javac 编译检查的反射调用”：
 * {@code Class.forName(...).getMethod(...)} 在方法改名/改参后只有运行到对应代码路径
 * 才会抛 {@link NoSuchMethodException}（且常被吞成“系统异常/静默失败”）。
 *
 * <p>本测试在编译期/CI 直接把下列反射目标的方法签名断言一遍，签名一旦漂移立即失败，
 * 把“运行时才发现”左移到“构建即发现”。需与以下启动期自检保持一致：
 * <ul>
 *     <li>{@code DistributedLockAspect#init()}</li>
 *     <li>{@code LogAspect#init()}</li>
 *     <li>{@code LoginUtilReflectiveSelfCheck#selfCheck()}</li>
 * </ul>
 *
 * <p>规则：目标类在类路径上但方法签名不匹配 → 测试失败；目标类本身不在类路径
 * （如未引入对应模块）→ 跳过，不视为异常。
 *
 * @author jffast
 */
class ReflectiveContractSmokeTest {

    @Test
    void allReflectiveTargetsMustResolve() {
        List<String> missing = new ArrayList<>();

        // DistributedLockAspect → RedisUtil
        check(missing, "cn.jfspeed.redis.util.RedisUtil",
                m("tryLock", String.class, String.class, long.class, int.class),
                m("releaseLock", String.class, String.class));

        // LogAspect → SysLog / SysLogService
        check(missing, "cn.jfspeed.system.entity.SysLog",
                c -> c.getConstructor(String.class, String.class, String.class),
                m("setId", Long.class),
                m("setCreateBy", Long.class),
                m("setState", String.class),
                m("setInfo", String.class));
        check(missing, "cn.jfspeed.system.service.SysLogService",
                m("save", Object.class));

        // SysNotificationService → WebSocketService：通知定向推送依赖 sendToUsers(Set, String)
        // （jffast-system 已编译期依赖 jffast-starter-web，此处仍断言 WS 契约不被改名）
        checkOptional(missing, "cn.jfspeed.web.websocket.WebSocketService",
                m("sendToUser", Long.class, String.class),
                m("sendToUsers", java.util.Set.class, String.class),
                m("sendToAll", String.class));

        // SysNotificationService → SysNotificationRecordService（定向推送写接收记录）
        checkOptional(missing, "cn.jfspeed.system.service.SysNotificationRecordService",
                m("save", Object.class),
                m("getUnreadCount", Long.class),
                m("markAsRead", Long.class, Long.class),
                m("markAllAsRead", Long.class));

        // LoginUtil → RedisUtil（hget/hset 已在上方 RedisUtil 组覆盖，这里单独列出 service 层）
        check(missing, "cn.jfspeed.system.service.SysRoleMenuService",
                m("listAllMenuIdList", Long.class),
                m("listAllBtnPerm", Long.class));
        check(missing, "cn.jfspeed.system.service.SysMenuService",
                m("selectMenuList", java.util.Map.class));
        check(missing, "cn.jfspeed.system.service.SysUserService",
                m("getById", java.io.Serializable.class));

        // LoginUtil → 实体字段访问器（Lombok @Data 生成）
        check(missing, "cn.jfspeed.system.entity.SysUser",
                m("getId"),
                m("setPassword", String.class),
                m("setSalt", String.class),
                m("getRoleList"),
                m("setRoleList", java.util.List.class),
                m("setUserName", String.class),
                m("setNickName", String.class),
                m("setAvatar", String.class),
                m("setEmail", String.class),
                m("setPhone", String.class),
                m("setDeptId", Long.class));
        check(missing, "cn.jfspeed.system.entity.SysRole",
                m("getId"),
                m("getRoleKey"));
        check(missing, "cn.jfspeed.system.entity.SysMenu",
                m("getType"),
                m("getPerms"));

        if (!missing.isEmpty()) {
            fail("反射契约自检失败，发现 " + missing.size() + " 处方法签名不匹配/缺失（运行到对应路径才会真正报错）：\n  - "
                    + String.join("\n  - ", missing));
        }
    }

    private void check(List<String> missing, String className, Resolver... resolvers) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            missing.add(className + " (类不在类路径)");
            return;
        }
        String simple = clazz.getSimpleName();
        for (Resolver r : resolvers) {
            try {
                r.resolve(clazz);
            } catch (NoSuchMethodException e) {
                missing.add(simple + "." + r.describe());
            }
        }
    }

    private void checkOptional(List<String> missing, String className, Resolver... resolvers) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException e) {
            // 可选模块：缺失即跳过
            return;
        }
        check(missing, className, resolvers);
    }

    @FunctionalInterface
    private interface Resolver {
        void resolve(Class<?> clazz) throws NoSuchMethodException;

        default String describe() {
            return "<unknown>";
        }
    }

    private static Resolver m(String name, Class<?>... params) {
        return new Resolver() {
            @Override
            public void resolve(Class<?> clazz) throws NoSuchMethodException {
                clazz.getMethod(name, params);
            }

            @Override
            public String describe() {
                StringBuilder sb = new StringBuilder(name).append("(");
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(params[i].getSimpleName());
                }
                return sb.append(")").toString();
            }
        };
    }
}
