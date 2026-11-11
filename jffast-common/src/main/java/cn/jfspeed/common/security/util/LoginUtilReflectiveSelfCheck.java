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

import cn.jfspeed.common.security.spi.LoginDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * LoginUtil 反射调用启动期自检。
 *
 * <p>LoginUtil 中仍有两类动态调用绕过 javac 的编译期检查，本自检在启动期把它们解析一遍：
 * <ul>
 *     <li><b>能力探测</b>（RedisUtil / Sa-Token）：探测的是可选依赖装没装，语义是能力开关，
 *     保持反射形式；签名漂移时输出醒目的 WARN 日志。</li>
 *     <li><b>会话内用户对象的动态访问器</b>（SysUser/SysRole/SysMenu 的 getter/setter）：
 *     操作对象是运行期会话里的实体，编译期无法绑定，保留反射形式；签名漂移时输出 WARN。</li>
 * </ul>
 *
 * <p>历史背景：LoginUtil 曾用 {@code Class.forName + invoke} 反射跨层调 system 服务
 * （SysRoleMenuService / SysMenuService / SysUserService）。该 4 处已由
 * {@link LoginDataSource} SPI 化（system 模块提供实现，编译期直调），签名漂移直接编译红，
 * 对应的反射自检随之失效化并移除。SPI 化后保留一项防御：
 * <b>system 模块在类路径上但 LoginDataSource 实现未注册</b>（组件扫描漏配等）→ WARN，
 * 此时鉴权数据源会按「warn + 返回空」降级，权限表现为空，必须提前发现。
 *
 * <p>说明：此处采用「日志告警」而非「启动即失败」，与历史口径一致；
 * 若要升级为启动即失败，把 {@code checkClass} 收集到的缺失项改为
 * {@code throw new IllegalStateException(...)} 即可。
 *
 * @author jffast
 */
@Slf4j
@Component
public class LoginUtilReflectiveSelfCheck {

    /** system 模块的标志类：类路径上有它而 LoginDataSource 未注册，即 SPI 实现漏注册 */
    private static final String SYSTEM_MODULE_MARKER_CLASS = "cn.jfspeed.system.service.SysRoleMenuService";

    private final ObjectProvider<LoginDataSource> loginDataSourceProvider;

    public LoginUtilReflectiveSelfCheck(ObjectProvider<LoginDataSource> loginDataSourceProvider) {
        this.loginDataSourceProvider = loginDataSourceProvider;
    }

    @PostConstruct
    public void selfCheck() {
        List<String> missing = new ArrayList<>();

        // RedisUtil（redis starter）—— LoginUtil 能力探测，保持反射
        checkClass(missing, "cn.jfspeed.redis.util.RedisUtil",
                method("hget", String.class, String.class),
                method("hset", String.class, String.class, Object.class));

        // SysUser 实体字段访问器（Lombok @Data 生成；会话内对象动态访问，保持反射）
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

        // SPI 实现漏注册防御：system 模块在类路径上但 LoginDataSource 未注册 → 数据源降级为空
        if (isClassPresent(SYSTEM_MODULE_MARKER_CLASS) && loginDataSourceProvider.getIfAvailable() == null) {
            log.warn("检测到 system 模块在类路径上，但 LoginDataSource SPI 实现未注册——"
                    + "菜单/权限数据将按空降级（登录用户无任何权限），请检查组件扫描是否覆盖 cn.jfspeed.system.security");
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

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, LoginUtilReflectiveSelfCheck.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
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
