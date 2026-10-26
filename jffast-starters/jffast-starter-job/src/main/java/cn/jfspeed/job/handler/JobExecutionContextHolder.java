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

import org.quartz.JobExecutionContext;

/**
 * @author jffast
 */
public class JobExecutionContextHolder {

    private static final ThreadLocal<JobExecutionContext> CONTEXT = new ThreadLocal<>();

    public static void set(JobExecutionContext context) {
        CONTEXT.set(context);
    }

    public static JobExecutionContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
