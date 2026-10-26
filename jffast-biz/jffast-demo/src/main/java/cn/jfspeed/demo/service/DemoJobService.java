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
package cn.jfspeed.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cn.jfspeed.job.annotation.JfFastJob;

/**
 * @author jffast
 */
@Service("demoJobService")
public class DemoJobService {

    private static final Logger log = LoggerFactory.getLogger(DemoJobService.class);

    @JfFastJob(name = "清理过期数据")
    public void cleanExpiredData() {
        log.info("====== 定时任务Demo：清理过期数据开始 ======");
        log.info("====== 定时任务Demo：清理过期数据完成 ======");
    }

    @JfFastJob(name = "生成报告", params = "reportType")
    public void generateReport(String reportType) {
        log.info("====== 定时任务Demo：生成{}报告开始 ======", reportType);
        log.info("====== 定时任务Demo：生成{}报告完成 ======", reportType);
    }
}
