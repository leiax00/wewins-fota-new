package com.wewins.fota.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.firmware.cache.FirmwareCacheRepository;
import com.wewins.fota.domain.firmware.cache.FirmwareListCacheRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis 产品固件列表缓存实现
 * <p>
 * 缓存产品的固件版本列表，支持滑动 TTL 续期
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisFirmwareListCacheRepository implements FirmwareListCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetricsService cacheMetricsService;
    private final FirmwareCacheRepository firmwareCacheRepository;
    private final CacheIndexService cacheIndexService;

    @Override
    public Optional<List<FirmwareVersion>> findByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }

        try {
            String key = buildFirmwareListKey(productId);
            Set<Object> versionIds = redisTemplate.opsForSet().members(key);

            if (versionIds == null || versionIds.isEmpty()) {
                cacheMetricsService.recordFirmwareCacheMiss();
                return Optional.empty();
            }

            List<String> firmwareKeys = versionIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(this::buildFirmwareKey)
                .collect(Collectors.toList());

            if (firmwareKeys.isEmpty()) {
                cacheMetricsService.recordFirmwareCacheMiss();
                return Optional.empty();
            }

            List<Object> cachedFirmwares = redisTemplate.opsForValue().multiGet(firmwareKeys);
            List<FirmwareVersion> firmwareList = deserializeFirmwareList(cachedFirmwares);

            cacheMetricsService.recordFirmwareCacheHit();
            renewTtl(key);
            return Optional.of(firmwareList);
        } catch (Exception e) {
            log.error("读取 Redis 产品固件列表缓存失败: productId={}", productId, e);
            cacheMetricsService.recordFirmwareCacheMiss();
            return Optional.empty();
        }
    }

    @Override
    public void cacheFirmwareList(Long productId, List<FirmwareVersion> firmwareList) {
        if (productId == null || firmwareList == null) {
            return;
        }

        try {
            for (FirmwareVersion firmware : firmwareList) {
                if (firmware != null && firmware.getId() != null) {
                    firmwareCacheRepository.cacheFirmware(firmware);
                }
            }

            String key = buildFirmwareListKey(productId);
            redisTemplate.delete(key);

            List<String> versionIds = firmwareList.stream()
                .filter(Objects::nonNull)
                .map(FirmwareVersion::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toList());

            if (!versionIds.isEmpty()) {
                redisTemplate.opsForSet().add(key, versionIds.toArray());
            }

            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
            
            String indexKey = String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId);
            cacheIndexService.addToIndex(indexKey, key);
            
            log.debug("产品固件列表缓存已更新: productId={}, count={}", productId, firmwareList.size());
        } catch (Exception e) {
            log.error("写入 Redis 产品固件列表缓存失败: productId={}", productId, e);
        }
    }

    @Override
    public void evict(Long productId) {
        if (productId == null) {
            return;
        }
        try {
            String key = buildFirmwareListKey(productId);
            redisTemplate.delete(key);
            log.debug("产品固件列表缓存已删除: productId={}", productId);
        } catch (Exception e) {
            log.error("删除 Redis 产品固件列表缓存失败: productId={}", productId, e);
        }
    }

    private String buildFirmwareListKey(Long productId) {
        return String.format(RedisKeyConstants.PRODUCT_FIRMWARE_LIST_KEY_TEMPLATE, productId);
    }

    private String buildFirmwareKey(String versionId) {
        return String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId);
    }

    private void renewTtl(String key) {
        try {
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("续期 Redis 产品固件列表缓存 TTL 失败: key={}", key, e);
        }
    }

    private List<FirmwareVersion> deserializeFirmwareList(List<Object> cachedFirmwares) {
        if (cachedFirmwares == null || cachedFirmwares.isEmpty()) {
            return List.of();
        }

        return cachedFirmwares.stream()
            .map(this::deserializeFirmware)
            .filter(Objects::nonNull)
            .toList();
    }

    private FirmwareVersion deserializeFirmware(Object cachedFirmware) {
        if (cachedFirmware == null) {
            return null;
        }
        if (cachedFirmware instanceof FirmwareVersion firmwareVersion) {
            return firmwareVersion;
        }
        try {
            return objectMapper.readValue(cachedFirmware.toString(), FirmwareVersion.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化固件失败: payload={}", cachedFirmware, e);
            return null;
        }
    }
}
