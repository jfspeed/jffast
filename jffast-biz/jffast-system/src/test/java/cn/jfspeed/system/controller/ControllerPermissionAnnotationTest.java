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
package cn.jfspeed.system.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;

import cn.dev33.satoken.annotation.SaCheckPermission;

/**
 * 控制器权限注解扫描（P2 整改）：所有非登录控制器的 DELETE 端点必须显式 {@code @SaCheckPermission}。
 * <p>
 * 登录控制器（SysLoginController）的 logout 等由 Sa-Token 登录态本身保护，列入白名单。
 *
 * @author jffast
 */
class ControllerPermissionAnnotationTest {

    private static final Class<?>[] CONTROLLERS = {
            SysUserController.class,
            SysRoleController.class,
            SysDeptController.class,
            SysMenuController.class,
            SysDictController.class,
            SysConfigController.class,
            SysLogController.class,
            SysJobController.class,
            SysJobLogController.class,
            SysCacheController.class,
            SysOnlineController.class,
            SysNotificationController.class,
            SysNotificationTemplateController.class,
            SysNotificationTemplateGroupController.class,
            SysDashboardController.class,
            SysMonitorController.class,
            CodeGenController.class,
    };

    @Test
    @DisplayName("非登录控制器的每个 @DeleteMapping 方法都应有 @SaCheckPermission")
    void deleteEndpointsShouldDeclarePermission() {
        List<String> missing = new ArrayList<>();
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(DeleteMapping.class)
                        && !method.isAnnotationPresent(SaCheckPermission.class)) {
                    missing.add(controller.getSimpleName() + "#" + method.getName());
                }
            }
        }
        assertTrue(missing.isEmpty(), "缺少 @SaCheckPermission 的 DELETE 端点: " + missing);
    }

    @Test
    @DisplayName("非登录控制器至少应有一个方法声明 @SaCheckPermission（防整类裸奔）")
    void controllersShouldNotBeCompletelyUnprotected() {
        List<String> bare = new ArrayList<>();
        for (Class<?> controller : CONTROLLERS) {
            boolean any = false;
            for (Method method : controller.getDeclaredMethods()) {
                if (method.isAnnotationPresent(SaCheckPermission.class)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                bare.add(controller.getSimpleName());
            }
        }
        assertTrue(bare.isEmpty(), "整类无权限注解的控制器: " + bare);
        assertFalse(bare.contains("SysLoginController"));
    }
}
