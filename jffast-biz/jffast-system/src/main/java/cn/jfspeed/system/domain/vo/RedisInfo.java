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

import java.util.Map;

/**
 * Redis 信息
 *
 * @author jffast
 */
@Data
public class RedisInfo {

    /** Redis 版本 */
    private String redisVersion;

    /** 运行模式 */
    private String mode;

    /** 端口 */
    private String port;

    /** 客户端数量 */
    private long connectedClients;

    /** 运行时间（秒） */
    private long uptimeInSeconds;

    /** 运行时间（格式化） */
    private String uptimeStr;

    /** 使用内存 */
    private long usedMemory;

    /** 使用内存（格式化） */
    private String usedMemoryHuman;

    /** AOF 是否开启 */
    private String aofEnabled;

    /** RDB 是否成功 */
    private String rdbLastBgsaveStatus;

    /** Key 数量 */
    private long dbKeys;

    /** 网络输入（Redis INFO 返回浮点值，如 3.79） */
    private double instantaneousInputKbps;

    /** 网络输出（Redis INFO 返回浮点值，如 3.79） */
    private double instantaneousOutputKbps;
}
