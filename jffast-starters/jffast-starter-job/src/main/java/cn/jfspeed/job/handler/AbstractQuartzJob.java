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

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jffast
 */
public abstract class AbstractQuartzJob implements Job {

    protected static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    @Override
    public final void execute(JobExecutionContext context) throws JobExecutionException {
        JobExecutionContextHolder.set(context);
        LocalDateTime startTime = LocalDateTime.now();
        Exception exception = null;
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
        }
        if (exception != null) {
            if (exception instanceof JobExecutionException) {
                throw (JobExecutionException) exception;
            }
            throw new JobExecutionException(exception);
        }
    }

    protected void before(JobExecutionContext context) {
    }

    protected abstract void doExecute(JobExecutionContext context) throws Exception;

    protected void after(JobExecutionContext context, LocalDateTime startTime, Exception exception) {
    }
}
