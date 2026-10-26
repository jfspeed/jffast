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

import cn.jfspeed.security.annotation.DistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式锁端点冒烟测试（集成级，默认禁用）。
 *
 * <p>门控：仅在 {@code -Djffast.smoke.integration=true} 时运行，且需要可用的 MySQL + Redis。
 * 普通 {@code mvn test} 不会触发（避免无基础设施时 CI 误红）。
 *
 * <p>价值：
 * <ol>
 *     <li>启动 Spring 上下文，触发 {@code DistributedLockAspect}/{@code LogAspect} 的
 *         {@code @PostConstruct} 反射自检——一旦 RedisUtil/SysLog 等签名漂移，
 *         上下文加载即失败（fail-fast）。</li>
 *     <li>反射收集 base 全部 {@link DistributedLock} 注解的控制器方法，确认这些端点存在，
 *         作为“遍历全部加锁端点”的轻量代理（真实 HTTP 最小保存需 DB + 鉴权，见下方扩展点）。</li>
 * </ol>
 *
 * <p>扩展点（接入真实 DB 后）：用 {@code MockMvc} / {@code TestRestTemplate} 对每个收集到的
 * {@code DistributedLock} 端点发最小合法 payload 做保存/更新，断言返回 code=200，即可等价于
 * “本次 demo 保存报系统繁忙”这类问题的回归防护。
 */
@SpringBootTest(classes = JfFastApplication.class)
@EnabledIfSystemProperty(named = "jffast.smoke.integration", matches = "true",
        disabledReason = "需要 MySQL+Redis；CI 用 -Djffast.smoke.integration=true 激活")
/**
 * @author jffast
 */
class DistributedLockEndpointSmokeTest {

    @Test
    void contextLoadsAndCollectsDistributedLockEndpoints(ApplicationContext ctx) {
        List<String> endpoints = new ArrayList<>();
        String[] controllerNames = ctx.getBeanNamesForAnnotation(RestController.class);
        for (String name : controllerNames) {
            Class<?> type = ctx.getBean(name).getClass();
            for (Method method : type.getMethods()) {
                if (method.isAnnotationPresent(DistributedLock.class)) {
                    endpoints.add(type.getSimpleName() + "#" + method.getName());
                }
            }
        }
        // 至少存在若干加锁端点（base 已知 >=12 个），为空则说明扫描/注解异常
        assertTrue(!endpoints.isEmpty(),
                "@DistributedLock 端点不应为空，请检查控制器扫描或注解：\n" + String.join("\n", endpoints));
    }
}
