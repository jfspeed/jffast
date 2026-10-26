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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * LoginUtil 反射调用启动期自检。
 *
 * <p>LoginUtil 出于模块解耦（common 不能依赖 system）大量使用 {@code Class.forName(...).getMethod(...)}。
 * 这类调用绕过了 javac 的编译期检查，方法改名/改参后只有运行到对应代码路径才会抛
 * {@link NoSuchMethodException}（且多数被吞成“系统异常/静默失败”）。
 *
 * <p>本自检在应用启动时把 LoginUtil 依赖的全部反射目标解析一遍：
 * <ul>
 *     <li>目标类在类路径上但方法签名不匹配 → 输出醒目的 WARN 日志（列出所有漂移的方法），便于“提前发现”；</li>
 *     <li>目标类本身不在类路径（如某些未引入 system 的场景）→ 直接跳过，不影响启动。</li>
 * </ul>
 *
 * <p>说明：此处采用“日志告警”而非“启动即失败”，是因为 LoginUtil 的若干反射调用历史上即有
 * try/catch 容错（例如 {@code getById} 在 MyBatis-Plus 下实际签名是 {@code Serializable}，
 * 此前用 {@code Object.class} 解析不到会静默失败；现已修正为 {@code Serializable.class}）。
 * 若要把这些点也升级为启动即失败，把下方 {@code checkClass} 收集到的缺失项改为
 * {@code throw new IllegalStateException(...)} 即可。
 *
 * @author jffast
 */
@Slf4j
@Component
public class LoginUtilReflectiveSelfCheck {

    @PostConstruct
    public void selfCheck() {
        List<String> missing = new ArrayList<>();

        // RedisUtil（redis starter）
        checkClass(missing, "cn.jfspeed.redis.util.RedisUtil",
                method("hget", String.class, String.class),
                method("hset", String.class, String.class, Object.class));

        // SysRoleMenuService
        checkClass(missing, "cn.jfspeed.system.service.SysRoleMenuService",
                method("listAllMenuIdList", Long.class),
                method("listAllBtnPerm", Long.class));

        // SysMenuService
        checkClass(missing, "cn.jfspeed.system.service.SysMenuService",
                method("selectMenuList", java.util.Map.class));

        // SysUserService（MyBatis-Plus IService.getById 实际签名为 Serializable，必须用 Serializable.class）
        checkClass(missing, "cn.jfspeed.system.service.SysUserService",
                method("getById", java.io.Serializable.class));

        // SysUser 实体字段访问器（Lombok @Data 生成）
        checkClass(missing, "cn.jfspeed.system.entity.SysUser",
                method("getId"),
                method("setPassword", String.class),
                method("setSalt", String.class),
                method("getRoleList"),
                method("setRoleList", java.util.List.class),
                method("setUserName", String.class),
                method("setNickName", String.class),
                method("setAvatar", String.class),
                method("setEmail", String.class),
                method("setPhone", String.class),
                method("setDeptId", Long.class));

        // SysRole 实体字段访问器
        checkClass(missing, "cn.jfspeed.system.entity.SysRole",
                method("getId"),
                method("getRoleKey"));

        // SysMenu 实体字段访问器
        checkClass(missing, "cn.jfspeed.system.entity.SysMenu",
                method("getType"),
                method("getPerms"));

        if (missing.isEmpty()) {
            log.info("LoginUtil 反射自检通过：全部反射目标方法签名匹配");
        } else {
            log.warn("LoginUtil 反射自检发现 {} 处方法签名不匹配/缺失（运行到对应路径才会真正报错，请尽快核对）：\n  - {}",
                    missing.size(), String.join("\n  - ", missing));
        }
    }

    private void checkClass(List<String> missing, String className, MethodSpec... specs) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            // 目标类不在类路径（如未引入对应模块），跳过，不视为异常
            return;
        }
        String simpleName = clazz.getSimpleName();
        for (MethodSpec spec : specs) {
            try {
                spec.resolve(clazz);
            } catch (NoSuchMethodException e) {
                missing.add(simpleName + "." + spec.describe());
            }
        }
    }

    @FunctionalInterface
    private interface MethodSpec {
        Method resolve(Class<?> clazz) throws NoSuchMethodException;

        default String describe() {
            return "<unknown>";
        }
    }

    private static final class M implements MethodSpec {
        private final String name;
        private final Class<?>[] params;

        M(String name, Class<?>... params) {
            this.name = name;
            this.params = params;
        }

        @Override
        public Method resolve(Class<?> clazz) throws NoSuchMethodException {
            return clazz.getMethod(name, params);
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
    }

    // 让 lambda 写法更简洁的辅助方法
    private static MethodSpec method(String name, Class<?>... params) {
        return new M(name, params);
    }
}
