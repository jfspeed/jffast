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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * 配置契约：actuator 端点默认收敛（P2）、健康探针免登录、异步队列与下载上限开关可配。
 *
 * @author jffast
 */
class ActuatorExposureContractTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resource(String path) throws IOException {
        try (InputStream in = ActuatorExposureContractTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "缺少资源：" + path);
            return new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String... keys) {
        Map<String, Object> current = root;
        for (String key : keys) {
            Object value = current.get(key);
            assertNotNull(value, "缺少配置节：" + String.join(".", keys));
            current = (Map<String, Object>) value;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static List<String> exposureInclude(Map<String, Object> root) {
        Map<String, Object> exposure = section(root, "management", "endpoints", "web", "exposure");
        return Arrays.stream(String.valueOf(exposure.get("include")).split(","))
                .map(String::trim)
                .toList();
    }

    @Test
    @DisplayName("默认/生产仅暴露 health,info；dev 才额外开放 metrics/prometheus")
    void actuatorExposureIsEnvironmentScoped() throws IOException {
        assertEquals(List.of("health", "info"), exposureInclude(resource("/application-web-actuator.yml")),
                "starter 默认暴露应仅为 health,info");
        assertEquals(List.of("health", "info"), exposureInclude(resource("/application-prod.yml")),
                "生产暴露应仅为 health,info");

        List<String> dev = exposureInclude(resource("/application-dev.yml"));
        assertTrue(dev.contains("metrics") && dev.contains("prometheus"),
                "dev 才允许额外开放指标端点，实际：" + dev);
    }

    @Test
    @DisplayName("健康探针在未登录时可达")
    @SuppressWarnings("unchecked")
    void healthProbeIsExcludedFromAuth() throws IOException {
        Map<String, Object> security = section(resource("/application.yml"), "jffast", "security");
        List<String> excludes = (List<String>) security.get("auth-excludes");
        assertTrue(excludes.contains("/actuator/health/**"),
                "健康探针必须加入鉴权排除，否则容器/K8s 探针被判 401");
    }

    @Test
    @DisplayName("异步队列容量与文件下载上限均有显式默认值")
    void asyncAndDownloadLimitsConfigurable() throws IOException {
        Map<String, Object> system = section(resource("/application.yml"), "jffast", "system");
        assertEquals(100, ((Number) section(system, "task").get("queue-capacity")).intValue(),
                "异步队列容量必须显式配置且 >0（SysTaskRunner 启动校验）");

        Map<String, Object> file = section(resource("/application.yml"), "jffast", "file");
        assertEquals(0, ((Number) file.get("download-max-bytes")).intValue(),
                "文件下载上限必须可配（0=不限制）");
    }
}
