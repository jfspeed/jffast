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
package cn.jfspeed.redis.util;

/**
 * @author jffast
 * @date 2024/1/15 10:16
 * @desc
 */

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RedisUtil {
    private static RedisTemplate redisTemplate;

    // 锁的过期时间（毫秒），避免死锁
    private static final long LOCK_EXPIRE_TIME = 3000L;
    // 锁的重试间隔（毫秒）
    private static final long LOCK_RETRY_INTERVAL = 100L;
    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 0;
    private RedisUtil() {
    }

    public RedisUtil(RedisTemplate redisTemplate) {
        RedisUtil.redisTemplate = redisTemplate;
    }


    /**
     * 获取分布式锁（带重试）
     * @param lockKey 锁Key
     * @param requestId 请求ID（防误删）
     * @return 是否获取成功
     */
    public static boolean tryLock(String lockKey, String requestId) {
        return tryLock(lockKey, requestId, LOCK_EXPIRE_TIME, MAX_RETRY_COUNT);
    }
    public static boolean tryLock(String lockKey, String requestId, long expireTime) {
        return tryLock(lockKey, requestId, expireTime, MAX_RETRY_COUNT);
    }

    /**
     * 带参数的分布式锁获取
     * @param lockKey 锁Key
     * @param requestId 请求ID（UUID，防误删）
     * @param expireTime 锁过期时间（毫秒）
     * @param retryCount 重试次数（0 表示不重试，只尝试一次）
     * @return 是否获取成功
     */
    public static boolean tryLock(String lockKey, String requestId, long expireTime, int retryCount) {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireTime, TimeUnit.MILLISECONDS);
        if (Boolean.TRUE.equals(success)) {
            log.info("获取分布式锁成功，lockKey：{}", lockKey);
            return true;
        }

        int count = 0;
        while (count < retryCount) {
            count++;
            log.warn("获取分布式锁失败，重试第{}次，lockKey：{}", count, lockKey);
            try {
                Thread.sleep(LOCK_RETRY_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            success = redisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireTime, TimeUnit.MILLISECONDS);
            if (Boolean.TRUE.equals(success)) {
                log.info("获取分布式锁成功（重试第{}次），lockKey：{}", count, lockKey);
                return true;
            }
        }
        log.error("获取分布式锁失败，已达最大重试次数，lockKey：{}", lockKey);
        return false;
    }

    /**
     * 释放分布式锁（Lua脚本，原子操作，防误删）
     * @param lockKey 锁Key
     * @param requestId 请求ID（必须和加锁时一致）
     * @return 是否释放成功
     */
    public static boolean releaseLock(String lockKey, String requestId) {
        String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(luaScript);
        redisScript.setResultType(Long.class);

        Object res = redisTemplate.execute(redisScript, Collections.singletonList(lockKey), requestId);
        if (res == null) {
            log.warn("释放分布式锁失败，脚本返回值为空，lockKey：{}", lockKey);
            return false;
        }
        Number resultNumber = (Number) res;
        long resultLong = resultNumber.longValue();
        if (resultLong > 0) {
            log.info("释放分布式锁成功，lockKey：{}，返回值：{}", lockKey, resultLong);
            return true;
        }
        log.warn("释放分布式锁失败（锁不存在或requestId不匹配），lockKey：{}，返回值：{}", lockKey, resultLong);
        return false;
    }

    /**
     * 累加次数并校验是否超过限制（核心限制方法）
     * 时间窗口内原子累加，超过最大次数则返回false，否则返回true
     * @param key 唯一标识（如登录场景：limit:login:用户账号/IP）
     * @param timeWindow 时间窗口，单位：秒（5分钟=300秒）
     * @param maxCount 时间窗口内最大允许次数（如连续失败5次则限制）
     * @return true-未超过限制（可继续操作），false-超过限制（拒绝操作）
     */
    public static boolean limitCountAndCheck(String key, long timeWindow, int maxCount) {
        // 1. 原子累加次数（Redis单命令原子性，避免并发下次数统计错误）
        long currentCount = RedisUtil.incr(key, 1);

        // 2. 首次累加（count=1），设置过期时间（仅首次设置，保证时间窗口有效性）
        if (currentCount == 1) {
            RedisUtil.expire(key, timeWindow);
        }

        // 3. 判断当前次数是否超过限制
        return currentCount <= maxCount;
    }

    /**
     * 清除指定key的限制次数（手动调用，如用户手动重置*
     * 直接删除Redis中对应的统计key，实现次数清零、限制重*
     * @param key 与limitCountAndCheck一致的唯一标识
     */
    public static void limitCountClear(String key) {
        // 直接删除Redis中对应的key，清除次数记录，重置限制
        RedisUtil.del(key);
    }

    public static boolean expire(String key, long time) {
        try {
            if (time > 0L) {
                redisTemplate.expire(key, time, TimeUnit.SECONDS);
            }

            return true;
        } catch (Exception var4) {
            log.error("Operation failed", var4);
            return false;
        }
    }

    public static long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    public static boolean hasKey(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception var2) {
            log.error("Operation failed", var2);
            return false;
        }
    }

    public static void del(String... key) {
        if (key != null && key.length > 0) {
            if (key.length == 1) {
                redisTemplate.delete(key[0]);
            } else {
                redisTemplate.delete(CollectionUtils.arrayToList(key));
            }
        }

    }

    public static <T> T get(String key) {
        ValueOperations<String, T> operation = redisTemplate.opsForValue();
        return operation.get(key);
    }
    public static void convertAndSend(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    public static <T> boolean set(String key, T valueSeconds) {
        try {
            redisTemplate.opsForValue().set(key, valueSeconds);
            return true;
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return false;
        }
    }

    public static <T> boolean set(String key, T value, long timeSeconds) {
        try {
            if (timeSeconds > 0L) {
                redisTemplate.opsForValue().set(key, value, timeSeconds, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }

            return true;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return false;
        }
    }

    public static <T> String[] allKey(Object obj) {
        Set<String> keys = new java.util.HashSet<>();
        org.springframework.data.redis.core.Cursor<String> cursor = redisTemplate.scan(
                org.springframework.data.redis.core.ScanOptions.scanOptions().match(obj.toString()).count(1000).build());
        while (cursor.hasNext()) {
            keys.add(cursor.next());
        }
        try {
            cursor.close();
        } catch (Exception e) {
            log.error("关闭Redis扫描游标失败", e);
        }
        return keys.toArray(new String[0]);
    }

    public static long incr(String key, long delta) {
        if (delta < 0L) {
            throw new RuntimeException("递增因子必须大于0");
        } else {
            return redisTemplate.opsForValue().increment(key, delta);
        }
    }

    public static long decr(String key, long delta) {
        if (delta < 0L) {
            throw new RuntimeException("递减因子必须大于0");
        } else {
            return redisTemplate.opsForValue().decrement(key, delta);
        }
    }

    public static long strLen(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        return (long) value.toString().length();
    }

    public static boolean append(String key, String str) {
        try {
            redisTemplate.opsForValue().append(key, str);
            return true;
        } catch (Exception var3) {
            return false;
        }
    }

    public static <T> T hget(String key, String item) {
        HashOperations<String, String, T> opsForHash = redisTemplate.opsForHash();
        return opsForHash.get(key, item);
    }

    public static <T> Map<String, T> hmget(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public static <T> boolean hmset(String key, Map<String, T> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return false;
        }
    }

    public static <T> boolean hmset(String key, Map<String, T> map, long valueSeconds) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (valueSeconds > 0L) {
                expire(key, valueSeconds);
            }

            return true;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return false;
        }
    }

    public static <T> boolean hset(String key, String item, T value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception var4) {
            log.error("Operation failed", var4);
            return false;
        }
    }

    public static <T> boolean hset(String key, String item, T value, long time) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            if (time > 0L) {
                expire(key, time);
            }

            return true;
        } catch (Exception var6) {
            log.error("Operation failed", var6);
            return false;
        }
    }

    public static <T> void hdel(String key, T... item) {
        redisTemplate.opsForHash().delete(key, item);
    }

    public static boolean hHasKey(String key, String item) {
        return redisTemplate.opsForHash().hasKey(key, item);
    }

    public static double hincr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, by);
    }

    public static double hdecr(String key, String item, double by) {
        return redisTemplate.opsForHash().increment(key, item, -by);
    }

    public static <T> Set<T> sGet(String key) {
        try {
            return redisTemplate.opsForSet().members(key);
        } catch (Exception var2) {
            log.error("Operation failed", var2);
            return null;
        }
    }

    public static boolean sHasKey(String key, Object value) {
        try {
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return false;
        }
    }

    public static <T> long sSet(String key, T... values) {
        try {
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return 0L;
        }
    }

    public static <T> long sSetAndTime(String key, long time, T... values) {
        try {
            Long count = redisTemplate.opsForSet().add(key, values);
            if (time > 0L) {
                expire(key, time);
            }

            return count;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return 0L;
        }
    }

    public static long sGetSetSize(String key) {
        try {
            return redisTemplate.opsForSet().size(key);
        } catch (Exception var2) {
            log.error("Operation failed", var2);
            return 0L;
        }
    }

    public static long setRemove(String key, Object... values) {
        try {
            Long count = redisTemplate.opsForSet().remove(key, values);
            return count;
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return 0L;
        }
    }

    public static <T> List<T> lGet(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception var6) {
            log.error("Operation failed", var6);
            return null;
        }
    }

    public static long lGetListSize(String key) {
        try {
            return redisTemplate.opsForList().size(key);
        } catch (Exception var2) {
            log.error("Operation failed", var2);
            return 0L;
        }
    }

    public static Object lGetIndex(String key, long index) {
        try {
            return redisTemplate.opsForList().index(key, index);
        } catch (Exception var4) {
            log.error("Operation failed", var4);
            return null;
        }
    }

    public static <T> boolean lSet(String key, T value) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            return true;
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return false;
        }
    }

    public static <T> boolean lSet(String key, T value, long time) {
        try {
            redisTemplate.opsForList().rightPush(key, value);
            if (time > 0L) {
                expire(key, time);
            }

            return true;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return false;
        }
    }

    public static <T> boolean lSet(String key, List<T> value) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            return true;
        } catch (Exception var3) {
            log.error("Operation failed", var3);
            return false;
        }
    }

    public static <T> boolean lSet(String key, List<T> value, long time) {
        try {
            redisTemplate.opsForList().rightPushAll(key, value);
            if (time > 0L) {
                expire(key, time);
            }

            return true;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return false;
        }
    }

    public static <T> boolean lUpdateIndex(String key, long index, T value) {
        try {
            redisTemplate.opsForList().set(key, index, value);
            return true;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return false;
        }
    }

    public static long lRemove(String key, long count, Object value) {
        try {
            Long remove = redisTemplate.opsForList().remove(key, count, value);
            return remove;
        } catch (Exception var5) {
            log.error("Operation failed", var5);
            return 0L;
        }
    }
    /**
     * 向有序集合添加元素（score用于排序，此处用时间戳）
     * @param key *
     * @param value 元素*
     * @param score 排序分数（时间戳*
     * @return 操作是否成功
     */
    public static <T> boolean zAdd(String key, T value, double score) {
        try {
            redisTemplate.opsForZSet().add(key, value, score);
            return true;
        } catch (Exception e) {
            log.error("Operation failed", e);
            return false;
        }
    }

    /**
     * 按分数倒序查询有序集合（从大到小，最新的联系人在前面*
     * @param key *
     * @param start 起始索引*开始）
     * @param end 结束索引*1表示全部*
     * @return 倒序后的元素集合
     */
    public static <T> Set<T> zReverseRange(String key, long start, long end) {
        try {
            return redisTemplate.opsForZSet().reverseRange(key, start, end);
        } catch (Exception e) {
            log.error("Operation failed", e);
            return null;
        }
    }

    /**
     * 按分数倒序查询有序集合（包含分数，用于获取联系人最后消息时间戳*
     * @param key *
     * @param start 起始索引
     * @param end 结束索引
     * @return 包含元素和分数的集合
     */
    public static Set<ZSetOperations.TypedTuple<Object>> zReverseRangeWithScores(String key, long start, long end) {
        try {
            return redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        } catch (Exception e) {
            log.error("Operation failed", e);
            return null;
        }
    }

    /**
     * 获取有序集合中元素的分数（用于获取联系人最后消息时间戳*
     * @param key *
     * @param value 元素*
     * @return 元素对应的分数（时间戳）
     */
    public static <T> Double zGetScore(String key, T value) {
        try {
            return redisTemplate.opsForZSet().score(key, value);
        } catch (Exception e) {
            log.error("Operation failed", e);
            return null;
        }
    }

    private static final DateTimeFormatter DATE_FORMAT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMAT_MONTH = DateTimeFormatter.ofPattern("yyyyMM").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMAT_YEAR = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.systemDefault());

    public static long incrByDay(String businessKey) {
        return incrByDay(businessKey, new Date());
    }

    public static long incrByDay(String businessKey, Date date) {
        String key = businessKey + ":incr:daily:" + DATE_FORMAT_DAY.format(date.toInstant());
        long count = incr(key, 1);
        if (count == 1) {
            expire(key, 2 * 24 * 60 * 60);
        }
        return count;
    }

    public static long incrByMonth(String businessKey) {
        return incrByMonth(businessKey, new Date());
    }

    public static long incrByMonth(String businessKey, Date date) {
        String key = businessKey + ":incr:monthly:" + DATE_FORMAT_MONTH.format(date.toInstant());
        long count = incr(key, 1);
        if (count == 1) {
            expire(key, 60 * 24 * 60 * 60);
        }
        return count;
    }

    public static long incrByQuarter(String businessKey) {
        return incrByQuarter(businessKey, new Date());
    }

    public static long incrByQuarter(String businessKey, Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int quarter = (month / 3) + 1;
        String key = businessKey + ":incr:quarterly:" + year + "Q" + quarter;
        long count = incr(key, 1);
        if (count == 1) {
            expire(key, 180 * 24 * 60 * 60);
        }
        return count;
    }

    public static long incrByYear(String businessKey) {
        return incrByYear(businessKey, new Date());
    }

    public static long incrByYear(String businessKey, Date date) {
        String key = businessKey + ":incr:yearly:" + DATE_FORMAT_YEAR.format(date.toInstant());
        long count = incr(key, 1);
        if (count == 1) {
            expire(key, 730 * 24 * 60 * 60);
        }
        return count;
    }
}