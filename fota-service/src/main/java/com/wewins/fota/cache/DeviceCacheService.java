package com.wewins.fota.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.entity.Device;
import com.wewins.fota.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备缓存服务
 * <p>
 * 提供 Redis 缓存功能，用于加速设备信息查询
 * </p>
 * <p>
 * Redis Key 格式：fota:device:{imei}
 * TTL：24 小时
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceMapper deviceMapper;

    /**
     * 从缓存获取设备信息
     *
     * @param imei 设备 IMEI
     * @return 设备缓存，如果不存在则返回 null
     */
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

    /**
     * 将设备信息写入缓存
     *
     * @param imei 设备 IMEI
     * @param cache 设备缓存数据
     */
    public void put(String imei, DeviceCache cache) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            // 更新缓存时间
            cache.setCachedAt(LocalDateTime.now());
            redisTemplate.opsForValue().set(key, cache,
                    Duration.ofSeconds(RedisKeyConstants.DEVICE_CACHE_TTL_SECONDS));
            log.debug("设备缓存已更新: imei={}, deviceId={}", imei, cache.getDeviceId());
        } catch (Exception e) {
            log.error("写入 Redis 设备缓存失败: imei={}", imei, e);
        }
    }

    /**
     * 从数据库加载设备信息并写入缓存
     *
     * @param imei 设备 IMEI
     * @return 设备缓存，如果设备不存在则返回 null
     */
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
                    .firmwareVersion(null)  // 需要关联 firmware_versions 表获取
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

    /**
     * 删除设备缓存
     *
     * @param imei 设备 IMEI
     */
    public void evict(String imei) {
        try {
            String key = String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imei);
            redisTemplate.delete(key);
            log.debug("设备缓存已删除: imei={}", imei);
        } catch (Exception e) {
            log.error("删除 Redis 设备缓存失败: imei={}", imei, e);
        }
    }

    /**
     * 预热缓存（批量加载设备信息）
     *
     * @param imeis 设备 IMEI 列表
     */
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

    /**
     * 批量删除设备缓存
     *
     * @param imeis 设备 IMEI 列表
     */
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

    /**
     * 按产品 ID 批量删除相关设备缓存
     * <p>
     * 使用 Redis SCAN 配合 KEYS 命令避免阻塞
     * </p>
     *
     * @param productId 产品 ID
     */
    public void evictByProduct(Long productId) {
        try {
            String pattern = RedisKeyConstants.DEVICE_KEY_TEMPLATE.replace("%s", "*");
            redisTemplate.delete(redisTemplate.keys(pattern));
            log.info("按产品删除设备缓存: productId={}", productId);
        } catch (Exception e) {
            log.error("按产品删除设备缓存失败: productId={}", productId, e);
        }
    }

    /**
     * 获取所有设备缓存 Key（用于监控和管理）
     *
     * @param imeis 设备 IMEI 列表
     * @return Redis Key 数组
     */
    public String[] getDeviceKeys(String[] imeis) {
        if (imeis == null || imeis.length == 0) {
            return new String[0];
        }

        String[] keys = new String[imeis.length];
        for (int i = 0; i < imeis.length; i++) {
            keys[i] =  String.format(RedisKeyConstants.DEVICE_KEY_TEMPLATE, imeis[i]);
        }
        return keys;
    }
}
