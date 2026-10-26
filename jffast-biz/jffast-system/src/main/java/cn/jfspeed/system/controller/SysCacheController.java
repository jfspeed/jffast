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
package cn.jfspeed.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.util.StrUtil;
import cn.jfspeed.common.core.base.BaseConstant;
import cn.jfspeed.common.core.exception.BusinessException;
import cn.jfspeed.common.web.domain.dto.R;
import cn.jfspeed.system.domain.vo.CacheInfo;
import cn.jfspeed.system.domain.vo.RedisInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 缓存管理
 *
 * @author jffast
 */
@Tag(name = "缓存管理")
@RestController
@RequestMapping("/sys/cache")
public class SysCacheController {

    /**
     * 应用缓存命名空间：缓存管理只允许触碰 jffast: 下的键，
     * 避免通过通配符或任意 key 读取/删除 Sa-Token 会话、分布式锁等基础设施键。
     */
    private static final String APP_CACHE_PREFIX = BaseConstant.CACHE_PREFIX;
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9_.-]+$");

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private String requireCacheName(String cacheName) {
        if (StrUtil.isBlank(cacheName) || !SAFE_SEGMENT.matcher(cacheName).matches()) {
            throw new BusinessException("非法缓存名称");
        }
        String fullPrefix = cacheName.endsWith(":") ? cacheName : cacheName + ":";
        if (!fullPrefix.startsWith(APP_CACHE_PREFIX)) {
            throw new BusinessException("只允许操作应用缓存（" + APP_CACHE_PREFIX + "*）");
        }
        return cacheName.endsWith(":") ? cacheName.substring(0, cacheName.length() - 1) : cacheName;
    }

    private String requireKey(String key) {
        if (StrUtil.isBlank(key) || key.indexOf('*') >= 0 || key.indexOf('?') >= 0
                || key.indexOf('[') >= 0 || key.indexOf(']') >= 0) {
            throw new BusinessException("非法缓存键名");
        }
        return key;
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:view")
    @Operation(summary = "获取 Redis 信息")
    @GetMapping("/redis/info")
    public R<RedisInfo> getRedisInfo() {
        RedisInfo info = new RedisInfo();

        Properties props = redisTemplate.execute((RedisCallback<Properties>) conn -> conn.info());
        if (props != null) {
            info.setRedisVersion(props.getProperty("redis_version", ""));
            info.setMode(props.getProperty("redis_mode", ""));
            info.setPort(props.getProperty("tcp_port", ""));
            info.setConnectedClients(Long.parseLong(props.getProperty("connected_clients", "0")));
            info.setUptimeInSeconds(Long.parseLong(props.getProperty("uptime_in_seconds", "0")));
            info.setUsedMemory(Long.parseLong(props.getProperty("used_memory", "0")));
            info.setUsedMemoryHuman(props.getProperty("used_memory_human", ""));
            info.setAofEnabled(props.getProperty("aof_enabled", "0"));
            info.setRdbLastBgsaveStatus(props.getProperty("rdb_last_bgsave_status", ""));
            info.setInstantaneousInputKbps(Double.parseDouble(props.getProperty("instantaneous_input_kbps", "0")));
            info.setInstantaneousOutputKbps(Double.parseDouble(props.getProperty("instantaneous_output_kbps", "0")));

            long uptime = info.getUptimeInSeconds();
            long days = uptime / 86400;
            long hours = (uptime % 86400) / 3600;
            long minutes = (uptime % 3600) / 60;
            info.setUptimeStr(days + "天" + hours + "小时" + minutes + "分钟");
        }

        Long size = redisTemplate.execute((RedisCallback<Long>) RedisConnection::dbSize);
        info.setDbKeys(size != null ? size : 0);

        return R.success(info);
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:view")
    @Operation(summary = "获取缓存名称列表")
    @GetMapping("/names")
    public R<List<String>> getCacheNames() {
        Set<String> cacheNames = new TreeSet<>();
        ScanOptions options = ScanOptions.scanOptions().match(APP_CACHE_PREFIX + "*").count(1000).build();
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            try (var cursor = conn.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8);
                    int index = key.indexOf(":");
                    if (index > 0) {
                        cacheNames.add(key.substring(0, index));
                    }
                }
            }
            return null;
        });
        return R.success(new ArrayList<>(cacheNames));
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:view")
    @Operation(summary = "根据缓存名称获取键列表")
    @GetMapping("/keys/{cacheName}")
    public R<CacheInfo> getCacheKeys(@PathVariable String cacheName) {
        cacheName = requireCacheName(cacheName);
        CacheInfo cacheInfo = new CacheInfo();
        cacheInfo.setCacheName(cacheName);

        List<String> keyList = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(cacheName + ":*").count(1000).build();
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            try (var cursor = conn.scan(options)) {
                while (cursor.hasNext()) {
                    keyList.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        cacheInfo.setKeys(keyList);
        cacheInfo.setKeyCount(keyList.size());

        return R.success(cacheInfo);
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:view")
    @Operation(summary = "根据缓存名和键名获取缓存值")
    @GetMapping("/value")
    public R<Object> getCacheValue(@RequestParam String cacheName, @RequestParam String key) {
        String fullKey = requireCacheName(cacheName) + ":" + requireKey(key);
        Object value = redisTemplate.opsForValue().get(fullKey);
        return R.success(value);
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:delete")
    @Operation(summary = "根据键名删除缓存")
    @DeleteMapping("/delete")
    public R<Void> deleteCache(@RequestParam String cacheName, @RequestParam String key) {
        String fullKey = requireCacheName(cacheName) + ":" + requireKey(key);
        Boolean result = redisTemplate.delete(fullKey);
        return result != null && result ? R.success() : R.error("删除失败");
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:delete")
    @Operation(summary = "清空指定缓存名称")
    @DeleteMapping("/clear/{cacheName}")
    public R<Void> clearCacheName(@PathVariable String cacheName) {
        cacheName = requireCacheName(cacheName);
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(cacheName + ":*").count(1000).build();
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            try (var cursor = conn.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        return R.success();
    }

    @SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN, value = "system:cache:delete")
    @Operation(summary = "清空全部缓存")
    @DeleteMapping("/clearAll")
    public R<Void> clearAll() {
        List<String> keys = new ArrayList<>();
        // 只清应用缓存命名空间；"*" 会连 Sa-Token 会话、登录失败计数、分布式锁一起删掉
        ScanOptions options = ScanOptions.scanOptions().match(APP_CACHE_PREFIX + "*").count(1000).build();
        redisTemplate.execute((RedisCallback<Void>) conn -> {
            try (var cursor = conn.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        return R.success();
    }
}
