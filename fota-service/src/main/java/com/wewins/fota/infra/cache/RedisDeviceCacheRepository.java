package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisDeviceCacheRepository implements DeviceCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;

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
            redisTemplate.opsForValue().set(key, cache,
                    RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.DEVICE_CACHE_TTL_SECONDS));
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
}
