package com.wewins.fota.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.firmware.cache.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisFirmwareCacheRepository implements FirmwareCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetricsService cacheMetricsService;

    @Override
    public Optional<FirmwareVersion> findById(Long versionId) {
        try {
            String key = buildFirmwareKey(versionId);
            Object cached = redisTemplate.opsForValue().get(key);
            
            if (cached != null) {
                renewTtl(key);
                FirmwareVersion firmware = deserializeFirmware(cached.toString());
                if (firmware != null) {
                    cacheMetricsService.recordFirmwareCacheHit();
                    return Optional.of(firmware);
                }
            }
            cacheMetricsService.recordFirmwareCacheMiss();
        } catch (Exception e) {
            log.error("从 Redis 获取固件缓存失败: versionId={}", versionId, e);
            cacheMetricsService.recordFirmwareCacheMiss();
        }
        return Optional.empty();
    }

    @Override
    public void cacheFirmware(FirmwareVersion firmware) {
        try {
            String key = buildFirmwareKey(firmware.getId());
            String json = serializeFirmware(firmware);
            
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_CACHE_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
            
            log.debug("固件缓存已更新: versionId={}", firmware.getId());
        } catch (Exception e) {
            log.error("写入 Redis 固件缓存失败: versionId={}", firmware.getId(), e);
        }
    }

    @Override
    public void evict(Long versionId) {
        try {
            String key = buildFirmwareKey(versionId);
            redisTemplate.delete(key);
            log.debug("固件缓存已删除: versionId={}", versionId);
        } catch (Exception e) {
            log.error("删除 Redis 固件缓存失败: versionId={}", versionId, e);
        }
    }

    
    private String buildFirmwareKey(Long versionId) {
        return String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId);
    }

    private void renewTtl(String key) {
        try {
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("续期固件缓存 TTL 失败: key={}", key, e);
        }
    }

    private String serializeFirmware(FirmwareVersion firmware) throws JsonProcessingException {
        return objectMapper.writeValueAsString(firmware);
    }

    private FirmwareVersion deserializeFirmware(String json) {
        try {
            return objectMapper.readValue(json, FirmwareVersion.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化固件缓存失败", e);
            return null;
        }
    }
}
