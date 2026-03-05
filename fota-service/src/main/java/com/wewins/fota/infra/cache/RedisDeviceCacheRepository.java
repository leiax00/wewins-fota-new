package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisDeviceCacheRepository implements DeviceCacheRepository {

    private static final int DEFAULT_EVICT_BATCH_SIZE = 500;
    private static final int MAX_EVICT_BATCH_SIZE = 5000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;

    @Value("${app.cache.device-evict-mode:collection}")
    private String deviceEvictMode;

    @Value("${app.cache.device-evict-batch-size:500}")
    private int evictBatchSize;

    @Override
    public DeviceCache get(String imei) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof DeviceCache) {
                cacheMetricsService.recordDeviceCacheHit();
                return (DeviceCache) cached;
            }
            cacheMetricsService.recordDeviceCacheMiss();
            return null;
        } catch (Exception e) {
            log.error("从 Redis 获取设备缓存失败: imei={}", imei, e);
            cacheMetricsService.recordDeviceCacheMiss();
            return null;
        }
    }

    @Override
    public void put(String imei, DeviceCache cache) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            cache.setCachedAt(LocalDateTime.now());
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.DEVICE_CACHE_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, cache, Duration.ofSeconds(ttl));
            log.debug("设备缓存已更新: imei={}, deviceId={}", imei, cache.getDeviceId());
        } catch (Exception e) {
            log.error("写入 Redis 设备缓存失败: imei={}", imei, e);
        }
    }

    @Override
    public void evict(String imei) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            redisTemplate.delete(key);
            log.debug("设备缓存已删除: imei={}", imei);
        } catch (Exception e) {
            log.error("删除 Redis 设备缓存失败: imei={}", imei, e);
        }
    }

    @Override
    public void evictBatch(List<String> imeis) {
        if (imeis == null || imeis.isEmpty()) {
            return;
        }
        try {
            Set<String> keySet = new LinkedHashSet<>(imeis.size());
            for (String imei : imeis) {
                if (imei != null && !imei.isBlank()) {
                    keySet.add(String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei));
                }
            }

            List<String> keys = new ArrayList<>(keySet);

            if (keys.isEmpty()) {
                return;
            }

            int batchSize = getBatchSize();
            String mode = deviceEvictMode == null ? "collection" : deviceEvictMode.trim().toLowerCase(Locale.ROOT);

            if ("pipeline".equals(mode)) {
                evictWithPipeline(keys, batchSize);
            } else {
                evictWithCollection(keys, batchSize);
            }

            log.info("批量删除设备缓存完成: requested={}, deletedKeys={}, mode={}, batchSize={}",
                    imeis.size(), keys.size(), mode, batchSize);
        } catch (Exception e) {
            log.error("批量删除 Redis 设备缓存失败: count={}", imeis.size(), e);
        }
    }

    private void evictWithCollection(List<String> keys, int batchSize) {
        for (int i = 0; i < keys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keys.size());
            redisTemplate.delete(keys.subList(i, end));
        }
    }

    private void evictWithPipeline(List<String> keys, int batchSize) {
        for (int i = 0; i < keys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keys.size());
            List<String> batch = keys.subList(i, end);
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                StringRedisConnection stringConnection = new DefaultStringRedisConnection(connection);
                for (String key : batch) {
                    stringConnection.del(key);
                }
                return null;
            });
        }
    }

    private int getBatchSize() {
        if (evictBatchSize <= 0) {
            return DEFAULT_EVICT_BATCH_SIZE;
        }
        return Math.min(evictBatchSize, MAX_EVICT_BATCH_SIZE);
    }
}
