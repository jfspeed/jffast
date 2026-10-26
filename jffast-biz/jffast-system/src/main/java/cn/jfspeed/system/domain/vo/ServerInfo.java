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
 * 服务器信息
 *
 * @author jffast
 */
@Data
public class ServerInfo {

    /** 操作系统名称 */
    private String osName;

    /** 系统架构 */
    private String osArch;

    /** 服务器 IP */
    private String serverIp;

    /** CPU 核心数 */
    private int cpuCores;

    /** CPU 使用率 */
    private double cpuUsage;

    /** 总内存（MB） */
    private long totalMemory;

    /** 已用内存（MB） */
    private long usedMemory;

    /** 空闲内存（MB） */
    private long freeMemory;

    /** 内存使用率 */
    private double memoryUsage;
}
