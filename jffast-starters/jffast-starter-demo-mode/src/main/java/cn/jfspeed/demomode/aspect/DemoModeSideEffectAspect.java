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
package cn.jfspeed.demomode.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.jfspeed.common.core.exception.BusinessException;

/**
 * 演示模式副作用拦截：文件落盘与定时任务手动触发直接拒绝（明示原因）。
 * <p>
 * 与 SQL 写短路的区别：上传/触发没有“假装成功”的诚实做法——文件不存在、
 * 任务没跑，返回成功只会让前端进入错乱状态，故抛业务异常转 {@code R.error}。
 * <br>
 * 代码生成器落盘无需拦截：JAR 运行时 {@code CodeGenerator.isRunningFromJar()}
 * 本就拒绝 {@code /save-local}；短信发送切 log 提供方（见 {@code application-demo.yml}）。
 *
 * @author jffast
 */
@Aspect
public class DemoModeSideEffectAspect {

    private static final Logger log = LoggerFactory.getLogger(DemoModeSideEffectAspect.class);

    /** 文件上传/删除：演示站不落盘 */
    @Before("execution(* cn.jfspeed.file.service.FileStorageService.upload*(..))"
            + " || execution(* cn.jfspeed.file.service.FileStorageService.delete(..))")
    public void blockFileWrite() {
        log.debug("[DemoMode] 文件写操作已拒绝");
        throw new BusinessException("演示环境不保存文件");
    }

    /** 定时任务手动触发：演示站不执行（调度器启动时已 pauseAll，这里防手动触发） */
    @Before("execution(* org.quartz.Scheduler.triggerJob(..))")
    public void blockJobTrigger() {
        log.debug("[DemoMode] 定时任务手动触发已拒绝");
        throw new BusinessException("演示环境不执行定时任务");
    }
}
