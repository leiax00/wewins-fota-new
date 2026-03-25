package com.wewins.fota.infra.cache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.firmware.repository.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.infra.cache.service.CacheMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
public class RedisFirmwareCacheRepository implements FirmwareCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper cacheObjectMapper;
    private final CacheMetricsService cacheMetricsService;

    public RedisFirmwareCacheRepository(
            RedisTemplate<String, Object> redisTemplate,
            CacheMetricsService cacheMetricsService) {
        this.redisTemplate = redisTemplate;
        this.cacheObjectMapper = new ObjectMapper();
        this.cacheObjectMapper.registerModule(new JavaTimeModule());
        this.cacheObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.cacheMetricsService = cacheMetricsService;
    }

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
        return cacheObjectMapper.writeValueAsString(firmware);
    }

    private FirmwareVersion deserializeFirmware(String json) {
        try {
            return cacheObjectMapper.readValue(json, FirmwareVersion.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化固件缓存失败", e);
            return null;
        }
    }
}
