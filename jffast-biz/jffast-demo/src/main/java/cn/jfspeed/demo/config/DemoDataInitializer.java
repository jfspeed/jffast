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
package cn.jfspeed.demo.config;

import cn.jfspeed.demo.entity.DemoExample;
import cn.jfspeed.demo.mapper.DemoExampleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author jffast
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "jffast.demo", name = "init-data", havingValue = "true", matchIfMissing = false)
public class DemoDataInitializer {

    private final DemoExampleMapper demoExampleMapper;

    @Bean
    public CommandLineRunner initDemoData() {
        return args -> {
            try {
                Long count = demoExampleMapper.selectCount(null);
                if (count == 0) {
                    String[] titles = {"Spring Boot 实战", "MyBatis-Plus 指南", "Vue 3 开发", "微服务架构", "Docker 部署"};
                    for (int i = 0; i < titles.length; i++) {
                        DemoExample entity = new DemoExample();
                        entity.setTitle(titles[i]);
                        entity.setContent("这是「" + titles[i] + "」的示例内容");
                        entity.setStatus(i % 2);
                        demoExampleMapper.insert(entity);
                    }
                    log.info("Demo module: initialized {} sample records", titles.length);
                }
            } catch (Exception e) {
                log.warn("Demo module: failed to initialize sample data: {}", e.getMessage());
            }
        };
    }
}
