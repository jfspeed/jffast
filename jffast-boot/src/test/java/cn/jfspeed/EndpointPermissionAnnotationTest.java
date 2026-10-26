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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * 端点权限注解硬门禁：classpath 上 cn.jfspeed 包内每个带映射的
 * {@code @RestController} 方法，必须满足二选一：
 * <ol>
 *   <li>方法或类级带 {@code @SaCheckPermission}；</li>
 *   <li>在本类 {@link #WHITELIST} 中登记，且写明「为什么可以没有权限码」。</li>
 * </ol>
 * <p>
 * 新增裸端点会被本测试直接 FAIL——先补权限码（首选），确实只需登录态/必须匿名的，
 * 才在白名单登记原因。白名单条目过期（对应方法已加注解或已删除）同样 FAIL，防止豁免堆积。
 *
 * @author jffast
 */
class EndpointPermissionAnnotationTest {

    /**
     * 豁免白名单：{@code 简单类名#方法名} 或 {@code 简单类名#*}（整类），值为豁免理由。
     * 条目必须是「登录态即可」或「登录前必须匿名」的端点，且理由要能对上真实机制。
     */
    private static final Map<String, String> WHITELIST = new LinkedHashMap<>();

    static {
        // ---- 登录端点：登录前必须可达，由 Sa-Token 登录态校验兜底 ----
        WHITELIST.put("SysLoginController#*",
                "验证码/登录/手机号登录/登出：登录前匿名可达，走 Sa-Token 登录校验而非权限校验");
        // ---- OAuth2 授权码流程回调：无法携带登录态，已在 jffast.security.auth-excludes 放行 ----
        WHITELIST.put("OAuth2ClientFlowController#*",
                "authorize/callback 回调无法携带登录态，已在 jffast.security.auth-excludes 放行");
        // ---- 社交登录：授权跳转匿名 + 个人绑定管理仅需登录态 ----
        WHITELIST.put("SocialLoginController#*",
                "authorize/callback 为登录前匿名跳转；bind/unbind/listBinds 是个人维度绑定管理，仅需登录态");
        // ---- 本人自助功能：userId 取自登录态，不设权限码（普通用户也必须能用） ----
        WHITELIST.put("SysUserController#getLoginUserInfo", "本人信息，登录态即可");
        WHITELIST.put("SysUserController#updatePassword", "本人改密（内部强制 loginId + @RateLimit），登录态即可");
        WHITELIST.put("SysUserController#updateProfile", "本人资料，登录态即可");
        WHITELIST.put("SysUserController#upload", "通用附件上传（个人资料场景），登录态 + @RateLimit 兜底");
        WHITELIST.put("SysUserController#uploadHead", "本人头像上传，登录态 + @RateLimit 兜底");
        WHITELIST.put("SysMenuController#routes", "返回当前用户自己的菜单树（前端装配路由），登录态即可");
        WHITELIST.put("SysNotificationController#markAllAsRead", "本人通知，userId 取自登录态");
        WHITELIST.put("SysNotificationController#markAsRead", "本人通知，userId 取自登录态且服务层校验归属");
        WHITELIST.put("SysNotificationController#unreadCount", "本人通知未读角标，登录态即可");
        WHITELIST.put("SysNotificationController#userNotificationPage", "本人通知分页，登录态即可");
        WHITELIST.put("SysNotificationController#userNotifications", "本人通知列表，登录态即可");
        // ---- 已验签的服务间内部调用 ----
        // Sa-Token 注解鉴权必须先有登录会话，而服务间内部调用按设计没有会话；这些端点由
        // SaInterceptor 的 checkLogin（未登录直连 = 401）+ 内部共享密钥验签（伪造头验签不过
        // 同样落回匿名）双层把关，属「内部接口不由注解授权、改按验签层把关」的正规形态。
        // 带注解会让内部调用恒 401。
        WHITELIST.put("SysDashboardInnerController#*",
                "服务间内部端点（数据口径 = SysDashboardController 的带注解版本）：路径在 /internal/**（网关不路由），"
                        + "已验签内部调用放行，非内部调用由控制器显式校验超管/ system:log:view；服务间调用示例专用目标");
    }


    private static final Class<? extends Annotation>[] MAPPING_ANNOTATIONS = new Class[]{
            RequestMapping.class, GetMapping.class, PostMapping.class, PutMapping.class,
            DeleteMapping.class, PatchMapping.class};

    @Test
    @DisplayName("所有带映射的 RestController 方法：有 @SaCheckPermission 或在白名单登记理由")
    void everyMappedEndpointHasPermissionOrWhitelistEntry() throws Exception {
        Map<String, Boolean> bare = new TreeMap<>();
        Set<String> scannedClasses = new LinkedHashSet<>();
        int endpointCount = 0;

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition bd : scanner.findCandidateComponents("cn.jfspeed")) {
            String className = bd.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz = Class.forName(className, false,
                    Thread.currentThread().getContextClassLoader());
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                continue;
            }
            scannedClasses.add(clazz.getSimpleName());
            for (Method method : clazz.getDeclaredMethods()) {
                if (!hasMapping(method)) {
                    continue;
                }
                endpointCount++;
                if (hasPermission(method, clazz)) {
                    continue;
                }
                bare.put(clazz.getSimpleName() + "#" + method.getName(), false);
            }
        }

        assertTrue(endpointCount >= 100,
                "扫描到的端点过少（" + endpointCount + "）——classpath 或扫描范围可能异常");

        // 1) 裸端点必须在白名单登记（支持 简单类名#* 整类豁免）
        StringBuilder missing = new StringBuilder();
        for (Map.Entry<String, Boolean> entry : new TreeMap<>(bare).entrySet()) {
            String key = entry.getKey();
            String owner = key.substring(0, key.lastIndexOf('#'));
            if (WHITELIST.containsKey(key) || WHITELIST.containsKey(owner + "#*")) {
                bare.put(key, true);
            } else {
                missing.append("\n  - ").append(key);
            }
        }
        if (missing.length() > 0) {
            fail("裸端点未登记：" + missing
                    + "\n  处置：首选补 @SaCheckPermission；确实只需登录态/必须匿名的，"
                    + "在 " + EndpointPermissionAnnotationTest.class.getSimpleName()
                    + ".WHITELIST 中登记 key 与理由，并在端点处注释原因。");
        }

        // 2) 白名单条目必须真的覆盖到裸端点（防过期豁免）
        for (Map.Entry<String, String> entry : WHITELIST.entrySet()) {
            String key = entry.getKey();
            String owner = key.substring(0, key.lastIndexOf('#'));
            String method = key.substring(key.lastIndexOf('#') + 1);
            if (!scannedClasses.contains(owner)) {
                continue;
            }
            if (method.equals("*")) {
                assertTrue(bare.keySet().stream().anyMatch(k -> k.startsWith(owner + "#")),
                        "白名单条目过期（" + owner + " 已无裸端点）：" + key + " —— " + entry.getValue());
            } else {
                assertTrue(Boolean.TRUE.equals(bare.get(key)),
                        "白名单条目过期（" + key + " 已加权限码或已删除）：" + entry.getValue());
            }
        }

        assertFalse(bare.isEmpty(), "未发现任何裸端点，扫描逻辑可能失效");
    }

    private static boolean hasMapping(Method method) {
        for (Class<? extends Annotation> type : MAPPING_ANNOTATIONS) {
            if (method.isAnnotationPresent(type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPermission(Method method, Class<?> clazz) {
        if (method.isAnnotationPresent(SaCheckPermission.class)) {
            return true;
        }
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.isAnnotationPresent(SaCheckPermission.class)) {
                return true;
            }
        }
        return false;
    }
}
