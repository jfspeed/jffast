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
package cn.jfspeed.job.config;

import cn.jfspeed.job.lock.JobLock;
import cn.jfspeed.job.lock.JvmJobLock;
import cn.jfspeed.job.lock.RedisJobLock;
import cn.jfspeed.job.manager.QuartzManager;
import cn.jfspeed.job.scanner.JobAnnotationScanner;
import cn.jfspeed.job.scheduler.JobScheduler;
import cn.jfspeed.job.store.JdbcJobStore;
import cn.jfspeed.job.store.JobStore;
import cn.jfspeed.job.store.NoopJobStore;
import cn.jfspeed.job.util.SpringContextHolder;
import org.quartz.Scheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 定时任务 Starter 自动配置。
 *
 * <p>装配的东西：Quartz 封装、`@JfFastJob` 扫描、**任务登记与调度器**（{@link JobScheduler}）、
 * 任务存储（JdbcTemplate 直连 `sys_job`/`sys_job_log`）、以及可选的 Redis 执行锁。
 *
 * <p>「可选依赖」用嵌套配置 + `@ConditionalOnClass(name=...)`（字符串形式）实现，
 * 这样缺 spring-jdbc / spring-data-redis 时对应 Bean 安静缺席，而不会在加载配置类时就
 * `NoClassDefFoundError`（服务照常能启动）。
 *
 * @author jffast
 */
@AutoConfiguration
@ConditionalOnClass(Scheduler.class)
@EnableConfigurationProperties(JobProperties.class)
@ConditionalOnProperty(prefix = "jffast.job", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JfFastJobAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SpringContextHolder springContextHolder() {
        return new SpringContextHolder();
    }

    @Bean
    @ConditionalOnMissingBean
    public QuartzManager quartzManager(Scheduler scheduler) {
        return new QuartzManager(scheduler);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobAnnotationScanner jobAnnotationScanner() {
        return new JobAnnotationScanner();
    }

    /**
     * 调度器：**任何服务引入本 Starter 就拥有"登记 + 调度自己的任务"的能力**，
     * 不依赖业务 system 模块是否在 classpath 上。
     */
    @Bean
    @ConditionalOnMissingBean
    public JobScheduler jobScheduler(JobProperties properties, JobStore jobStore, QuartzManager quartzManager,
                                     JobAnnotationScanner scanner, Environment environment) {
        return new JobScheduler(properties, jobStore, quartzManager, scanner, environment);
    }

    /**
     * 有 JdbcTemplate（即接了数据库）时才注册真实存储；否则用空实现让调度器安静跳过。
     * <p>注意方法签名里出现了 {@code JdbcTemplate} ⇒ 必须放在带 `@ConditionalOnClass(name=...)` 的
     * 嵌套配置里，否则缺少 spring-jdbc 的服务会在解析本配置类时炸掉。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    static class JobStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean
        JobStore jobStore(ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jdbcTemplateProvider) {
            org.springframework.jdbc.core.JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
            return jdbcTemplate != null ? new JdbcJobStore(jdbcTemplate) : new NoopJobStore();
        }
    }

    /**
     * 有 Redis 时用 Redis 执行锁（多副本只跑一次）；只有类在但没配模板时退化为单 JVM 锁。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
    static class JobLockConfiguration {

        @Bean
        @ConditionalOnMissingBean
        JobLock jobLock(ObjectProvider<org.springframework.data.redis.core.RedisTemplate<String, Object>> provider) {
            org.springframework.data.redis.core.RedisTemplate<String, Object> template = provider.getIfAvailable();
            return template != null ? new RedisJobLock(template) : new JvmJobLock();
        }
    }
}
