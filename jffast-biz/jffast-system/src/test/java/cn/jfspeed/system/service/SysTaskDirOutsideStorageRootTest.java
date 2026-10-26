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
package cn.jfspeed.system.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cn.jfspeed.file.config.FileStorageProperties;

/**
 * 任务产物目录与文件存储根的边界（无需 Redis/MySQL）。
 * <p>
 * 存储根被 {@code WebMvcConfig} 整体映射成 {@code /upload/**}，又被 {@code SaTokenConfig} 列进
 * 免登录白名单——所以「产物目录是否在存储根之内」不是一个整理偏好问题，而是「异步导出的用户
 * 表格（手机号、邮箱）和管理员上传的待导入文件，任何人拿到 taskId 就能匿名下走」与「只有归属
 * 校验过的 {@code /sys/task/download} 能读」的分界。历史上产物就写在 {@code upload/task}，
 * 2026-10-26 第三轮复核发现并移出。
 * <p>
 * 移出之后仍可能被改回来（有人觉得「都在 upload 下好找」），所以这里同时钉住正例与反例：
 * 反例必须**启动即失败**，而不是运行时静默泄露。
 *
 * @author jffast
 */
class SysTaskDirOutsideStorageRootTest {

    private static FileStorageProperties storageRoot(String localPath) {
        FileStorageProperties properties = new FileStorageProperties();
        FileStorageProperties.Local local = new FileStorageProperties.Local();
        local.setPath(localPath);
        properties.setLocal(local);
        return properties;
    }

    @Test
    @DisplayName("产物目录配成存储根的子目录：等于匿名可下载，必须启动即失败")
    void taskDirInsideStorageRootFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new SysTaskRunner(storageRoot("upload"), 100, "upload/task"));

        assertTrue(ex.getMessage().contains("jffast.system.task.file-dir"),
                "提示要指名配置项，否则运维无从下手：" + ex.getMessage());
        assertTrue(ex.getMessage().contains("免登录"), "提示要说明后果：" + ex.getMessage());
    }

    @Test
    @DisplayName("产物目录配成存储根本身：同样拒绝（整个上传目录会被当成任务产物目录）")
    void taskDirEqualToStorageRootFailsFast() {
        assertThrows(IllegalStateException.class,
                () -> new SysTaskRunner(storageRoot("upload"), 100, "upload"));
    }

    @Test
    @DisplayName("与存储根同级的独立目录通过校验，解析为绝对路径")
    void siblingTaskDirIsAccepted() throws IOException {
        SysTaskRunner runner = new SysTaskRunner(
                storageRoot("target/upload-guard-test"), 100, "target/task-files-guard-test");

        Path dir = runner.taskDir();
        Path root = Paths.get("target/upload-guard-test").toAbsolutePath().normalize();

        assertTrue(dir.isAbsolute(), "任务目录应解析为绝对路径，避免工作目录变化后写偏：" + dir);
        assertFalse(dir.startsWith(root), "产物目录不得落在免登录静态映射的存储根内：" + dir);
    }
}
