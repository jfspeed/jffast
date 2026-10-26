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
package cn.jfspeed.system.domain.vo;

import lombok.Data;

/**
 * JVM 信息
 *
 * @author jffast
 */
@Data
public class JvmInfo {

    /** JVM 名称 */
    private String jvmName;

    /** JVM 版本 */
    private String jvmVersion;

    /** JVM 供应商 */
    private String jvmVendor;

    /** 启动时间 */
    private long startTime;

    /** 运行时间（毫秒） */
    private long upTime;

    /** 运行时间（格式化） */
    private String upTimeStr;

    /** 堆最大内存（MB） */
    private long heapMax;

    /** 堆已用内存（MB） */
    private long heapUsed;

    /** 堆空闲内存（MB） */
    private long heapFree;

    /** 堆使用率 */
    private double heapUsage;

    /** 非堆已用内存（MB） */
    private long nonHeapUsed;

    /** 线程数 */
    private int threadCount;

    /** 守护线程数 */
    private int daemonThreadCount;

    /** GC 次数 */
    private long gcCount;

    /** GC 耗时（毫秒） */
    private long gcTime;
}
