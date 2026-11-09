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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /upload/**} 静态映射必须下线（AC-01，本仓范围）。
 *
 * <p>静态资源映射把落盘目录直接暴露成 HTTP 路径，**不带任何归属校验**：
 * 拿到路径就能读，登录即可读任意路径（审计 P3-9 / H-4）。
 * 文件读取已收口到 {@code /sys/file/view/{id}?ticket=...}（签名直显）
 * 与 {@code /sys/file/download/{id}}（受控下载），两者都过归属 + 租户校验。
 *
 * <p>本测试只覆盖**本仓**：本仓是公开仓，测试代码里不允许出现其他仓库的路径与
 * 内部机制话术（公开前防泄露门禁会拦）。跨仓库的同口径扫描由部署侧门禁承担，
 * 两者合起来覆盖全部落点。
 *
 * @author jffast
 */
class UploadStaticMappingTest {

    /** 静态映射的典型写法：{@code addResourceHandler("/upload/**")} */
    private static final Pattern UPLOAD_RESOURCE_HANDLER =
            Pattern.compile("addResourceHandler\\s*\\(\\s*\"[^\"]*/upload/\\*\\*");

    /** 过滤器 URL 模式：{@code addUrlPatterns("/upload/*")} */
    private static final Pattern UPLOAD_URL_PATTERN =
            Pattern.compile("addUrlPatterns\\s*\\(\\s*\"[^\"]*/upload/\\*");

    /**
     * 本仓 {@code WebMvcConfig.java} 的位置（相对「仓根」）。
     *
     * <p>仓根 = 含 {@code jffast-starters} 的目录。从 cwd 逐级向上找——
     * surefire 的工作目录一般是模块目录，不硬编码层数。
     */
    private static final String CONFIG_REL =
            "jffast-starters/jffast-starter-web/src/main/java/cn/jfspeed/web/config/WebMvcConfig.java";

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("jffast-starters"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到仓根（cwd=" + Path.of("").toAbsolutePath() + "）");
    }

    @Test
    @DisplayName("本仓 WebMvcConfig 不得注册 /upload/** 静态映射")
    void noUploadStaticMapping() throws IOException {
        Path config = repoRoot().resolve(CONFIG_REL);
        assertTrue(Files.isRegularFile(config), "找不到本仓的 WebMvcConfig.java：" + config);

        String text = Files.readString(config, StandardCharsets.UTF_8);
        assertFalse(UPLOAD_RESOURCE_HANDLER.matcher(text).find(),
                "仍注册了 /upload 静态资源映射（登录即可读任意路径）：\n" + text);
        assertFalse(UPLOAD_URL_PATTERN.matcher(text).find(),
                "仍把过滤器挂在 /upload/* 上（登录即可读任意路径）：\n" + text);
    }

    @Test
    @DisplayName("本仓 WebMvcConfig 的安全响应头过滤器只挂在 /sys/file/view/*")
    void securityHeaderFilterTargetsViewEndpointOnly() throws IOException {
        Path target = repoRoot().resolve(CONFIG_REL);
        assertTrue(Files.isRegularFile(target), "找不到本仓的 WebMvcConfig.java：" + target);

        String text = Files.readString(target, StandardCharsets.UTF_8);
        assertTrue(text.contains("addUrlPatterns(\"/sys/file/view/*\")"),
                "安全响应头过滤器必须改挂到 /sys/file/view/*：\n" + text);
        assertFalse(text.contains("addUrlPatterns(\"/upload/*\")"),
                "安全响应头过滤器不得再挂 /upload/*");
    }
}
