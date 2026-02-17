package com.wewins.fota.cache.ratelimit;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 设备限流器 Redis 实现
 * <p>
 * 基于 Redis + Lua 实现固定窗口限流：
 * <ul>
 *   <li>使用 Lua 脚本保证 INCR + EXPIRE 的原子性</li>
 *   <li>支持固定窗口限流算法</li>
 *   <li>支持 LastSeen 更新限频</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDeviceRateLimiter implements DeviceRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> fixedWindowRateLimitScript;

    @Override
    public RateLimitDecision allow(String key, int maxRequests, Duration window) {
        try {
            // 构建限流键
            String rateLimitKey = buildRateLimitKey(key);

            // 执行 Lua 脚本：INCR + EXPIRE
            Long currentCount = redisTemplate.execute(
                    fixedWindowRateLimitScript,
                    Collections.singletonList(rateLimitKey),
                    String.valueOf(window.getSeconds())
            );

            if (currentCount == null) {
                log.warn("限流检查失败：返回值为空, key={}", key);
                return RateLimitDecision.denied("RATE_LIMIT_ERROR");
            }

            // 判断是否超过限制
            if (currentCount > maxRequests) {
                log.debug("请求被限流: key={}, current={}, max={}", key, currentCount, maxRequests);
                return RateLimitDecision.denied("RATE_LIMITED");
            }

            // 计算剩余配额和重置时间
            long remaining = maxRequests - currentCount;
            long resetAt = (System.currentTimeMillis() / 1000) + window.getSeconds();

            log.debug("限流检查通过: key={}, current={}, remaining={}", key, currentCount, remaining);
            return RateLimitDecision.allowed(remaining, resetAt);

        } catch (Exception e) {
            log.error("限流检查异常: key={}", key, e);
            // 异常情况降级：允许请求通过
            return RateLimitDecision.denied("RATE_LIMIT_ERROR");
        }
    }

    @Override
    public boolean allowLastSeenUpdate(String imei, Duration minInterval) {
        try {
            // 构建限流键
            String key = buildLastSeenKey(imei);

            // 执行 Lua 脚本
            Long currentCount = redisTemplate.execute(
                    fixedWindowRateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(minInterval.getSeconds())
            );

            // currentCount == 1 表示首次访问，允许更新
            boolean allowed = currentCount != null && currentCount == 1;

            if (allowed) {
                log.debug("LastSeen 更新允许: imei={}", imei);
            } else {
                log.debug("LastSeen 更新被限流: imei={}", imei);
            }

            return allowed;

        } catch (Exception e) {
            log.error("LastSeen 限流检查异常: imei={}", imei, e);
            // 异常情况降级：允许更新
            return true;
        }
    }

    /**
     * 构建限流键
     * <p>
     * 格式：fota:ratelimit:{key}:{minute}
     * </p>
     * <p>
     * 包含分钟级时间戳，避免固定窗口边界问题
     * </p>
     *
     * @param key 限流标识
     * @return Redis Key
     */
    private String buildRateLimitKey(String key) {
        // 使用分钟级时间戳作为键的一部分，避免不同分钟共用同一个计数器
        long minuteTimestamp = System.currentTimeMillis() / 60000;
        return String.format(RedisKeyConstants.RATE_LIMIT_KEY_TEMPLATE, key, minuteTimestamp);
    }

    /**
     * 构建 LastSeen 限流键
     * <p>
     * 格式：fota:ratelimit:lastseen:{imei}:{hour}
     * </p>
     *
     * @param imei 设备 IMEI
     * @return Redis Key
     */
    private String buildLastSeenKey(String imei) {
        // 使用小时级时间戳，支持每小时最多更新一次
        long hourTimestamp = System.currentTimeMillis() / 3600000;
        return String.format("fota:ratelimit:lastseen:%s:%d", imei, hourTimestamp);
    }
}
