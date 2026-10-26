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
package com.example.fixture;

import cn.jfspeed.job.annotation.JfFastJob;

/**
 * 非 {@code cn.jfspeed} 包下的任务 Bean。
 *
 * <p>仅用于验证调用目标白名单：包前缀不在允许列表内时，
 * 只有标注 {@code @JfFastJob} 的方法才可被调度调用。
 *
 * @author jffast
 */
public class ExternalJobBean {

    @JfFastJob(name = "外部示例任务")
    public void annotated() {
        // 测试替身，无逻辑
    }

    public void notAnnotated() {
        // 测试替身，无逻辑
    }
}
