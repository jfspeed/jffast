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
package cn.jfspeed.job.handler;

import java.time.LocalDateTime;

import cn.jfspeed.common.core.context.TenantScopeBridge;
import cn.jfspeed.job.util.SpringContextHolder;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jffast
 */
public abstract class AbstractQuartzJob implements Job {

    /** {@code JobDataMap} 中的租户归属键（由 {@code JobScheduler} 写入；缺省 = 平台级任务） */
    public static final String TENANT_KEY = "tenantId";

    protected static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    @Override
    public final void execute(JobExecutionContext context) throws JobExecutionException {
        JobExecutionContextHolder.set(context);
        LocalDateTime startTime = LocalDateTime.now();
        Exception exception = null;
        TenantScopeBridge bridge = SpringContextHolder.tryGetBean(TenantScopeBridge.class);
        Long previousTenantId = bridge == null ? null : bridge.currentTenantId();
        Long tenantId = resolveTenantId(context);
        if (bridge != null) {
            if (tenantId == null) {
                // 不允许「静默全量」：平台级任务必须在日志里显式声明，避免后续误以为它跑在某个租户下
                log.info("【定时任务】{}.{} 无租户归属，按平台级全量视角执行（显式声明）",
                        context.getJobDetail().getKey().getGroup(), context.getJobDetail().getKey().getName());
                bridge.detach();
            } else {
                bridge.attach(tenantId);
            }
        }
        try {
            before(context);
            doExecute(context);
        } catch (Exception e) {
            exception = e;
            log.error("定时任务执行失败: {}.{}", context.getJobDetail().getKey().getGroup(),
                    context.getJobDetail().getKey().getName(), e);
        } finally {
            after(context, startTime, exception);
            JobExecutionContextHolder.clear();
            if (bridge != null) {
                if (previousTenantId == null) {
                    bridge.detach();
                } else {
                    bridge.attach(previousTenantId);
                }
            }
        }
        if (exception != null) {
            if (exception instanceof JobExecutionException) {
                throw (JobExecutionException) exception;
            }
            throw new JobExecutionException(exception);
        }
    }

    /** 任务行上的租户归属（JobDataMap 里的 {@link #TENANT_KEY}）；缺省 = 平台级 */
    private static Long resolveTenantId(JobExecutionContext context) {
        Object raw = context.getMergedJobDataMap().get(TENANT_KEY);
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw.toString());
        } catch (NumberFormatException e) {
            log.warn("【定时任务】租户归属值无法解析为数字，按平台级处理: {}", raw);
            return null;
        }
    }

    protected void before(JobExecutionContext context) {
    }

    protected abstract void doExecute(JobExecutionContext context) throws Exception;

    protected void after(JobExecutionContext context, LocalDateTime startTime, Exception exception) {
    }
}
