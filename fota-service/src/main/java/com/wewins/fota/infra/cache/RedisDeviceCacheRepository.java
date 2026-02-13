package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.infra.persistence.mybatis.device.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DeviceCacheRepository 的 Redis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisDeviceCacheRepository implements DeviceCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceMapper deviceMapper;

    @Override
    public DeviceCache get(String imei) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof DeviceCache) {
                return (DeviceCache) cached;
            }
            return null;
        } catch (Exception e) {
            log.error("从 Redis 获取设备缓存失败: imei={}", imei, e);
            return null;
        }
    }

    @Override
    public void put(String imei, DeviceCache cache) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            cache.setCachedAt(LocalDateTime.now());
            redisTemplate.opsForValue().set(key, cache,
                    Duration.ofSeconds(RedisKeyConstants.DEVICE_CACHE_TTL_SECONDS));
            log.debug("设备缓存已更新: imei={}, deviceId={}", imei, cache.getDeviceId());
        } catch (Exception e) {
            log.error("写入 Redis 设备缓存失败: imei={}", imei, e);
        }
    }

    @Override
    public DeviceCache loadAndCache(String imei) {
        try {
            Device device = deviceMapper.selectByImei(imei);
            if (device == null) {
                log.warn("设备不存在: imei={}", imei);
                return null;
            }

            DeviceCache cache = DeviceCache.builder()
                    .deviceId(device.getId())
                    .productId(device.getProductId())
                    .firmwareVersion(null)
                    .policyId(null)
                    .cachedAt(LocalDateTime.now())
                    .build();

            put(imei, cache);
            return cache;
        } catch (Exception e) {
            log.error("从数据库加载设备信息失败: imei={}", imei, e);
            return null;
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
    public void warmUp(String[] imeis) {
        if (imeis == null || imeis.length == 0) {
            return;
        }

        int success = 0;
        for (String imei : imeis) {
            DeviceCache existing = get(imei);
            if (existing == null) {
                DeviceCache loaded = loadAndCache(imei);
                if (loaded != null) {
                    success++;
                }
            }
        }
        log.info("设备缓存预热完成: total={}, cached={}", imeis.length, success);
    }

    @Override
    public void evictBatch(String[] imeis) {
        if (imeis == null || imeis.length == 0) {
            return;
        }

        try {
            String[] keys = new String[imeis.length];
            for (int i = 0; i < imeis.length; i++) {
                keys[i] = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imeis[i]);
            }
            redisTemplate.delete(List.of(keys));
            log.info("批量删除设备缓存: count={}", imeis.length);
        } catch (Exception e) {
            log.error("批量删除 Redis 设备缓存失败", e);
        }
    }

    @Override
    public void evictByProduct(Long productId) {
        try {
            String pattern = RedisKeyConstants.DEVICE_KEY_TEMPLATE.replace("%s", "*");
            redisTemplate.delete(redisTemplate.keys(pattern));
            log.info("按产品删除设备缓存: productId={}", productId);
        } catch (Exception e) {
            log.error("按产品删除设备缓存失败: productId={}", productId, e);
        }
    }

    @Override
    public String[] getDeviceKeys(String[] imeis) {
        if (imeis == null || imeis.length == 0) {
            return new String[0];
        }

        String[] keys = new String[imeis.length];
        for (int i = 0; i < imeis.length; i++) {
            keys[i] = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imeis[i]);
        }
        return keys;
    }
}
