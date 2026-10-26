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

import java.util.List;

/**
 * 缓存信息
 *
 * @author jffast
 */
@Data
public class CacheInfo {

    /** 缓存名称 */
    private String cacheName;

    /** 缓存键数量 */
    private long keyCount;

    /** 缓存键列表 */
    private List<String> keys;
}
