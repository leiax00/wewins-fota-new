package com.wewins.fota.infra.cache.repository;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionLookupCacheRepository;
import com.wewins.fota.infra.cache.service.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisFirmwareVersionLookupCacheRepository implements FirmwareVersionLookupCacheRepository {

    private static final String NOT_FOUND_SENTINEL = "NF";
    private static final String NULL_TAG = "__NULL__";

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheMetricsService cacheMetricsService;

    @Override
    public CacheLookupResult<List<Long>> get(Long productId, String version, String internalVersion) {
        if (productId == null || !StringUtils.hasText(version)) {
            cacheMetricsService.recordFirmwareLookupCacheMiss();
            return CacheLookupResult.miss();
        }
        try {
            String key = buildKey(productId, version, internalVersion);
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached == null) {
                cacheMetricsService.recordFirmwareLookupCacheMiss();
                return CacheLookupResult.miss();
            }
            String value = cached.toString();
            if (NOT_FOUND_SENTINEL.equals(value)) {
                cacheMetricsService.recordFirmwareLookupCacheMiss();
                return CacheLookupResult.hitNotFound();
            }
            List<Long> versionIds = Arrays.stream(value.split(","))
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .toList();
            renewTtl(key);
            cacheMetricsService.recordFirmwareLookupCacheHit();
            return CacheLookupResult.hit(versionIds);
        } catch (Exception e) {
            log.warn("读取固件版本映射缓存失败: productId={}, version={}, internalVersion={}",
                    productId, version, internalVersion, e);
            cacheMetricsService.recordFirmwareLookupCacheMiss();
            return CacheLookupResult.miss();
        }
    }

    @Override
    public void put(Long productId, String version, String internalVersion, List<Long> versionIds) {
        if (productId == null || !StringUtils.hasText(version) || versionIds == null || versionIds.isEmpty()) {
            return;
        }
        try {
            String key = buildKey(productId, version, internalVersion);
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_LOOKUP_CACHE_TTL_SECONDS);
            String value = versionIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("写入固件版本映射缓存失败: productId={}, version={}, internalVersion={}, versionIds={}",
                    productId, version, internalVersion, versionIds, e);
        }
    }

    @Override
    public void putNotFound(Long productId, String version, String internalVersion) {
        if (productId == null || !StringUtils.hasText(version)) {
            return;
        }
        try {
            String key = buildKey(productId, version, internalVersion);
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_LOOKUP_NOT_FOUND_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, NOT_FOUND_SENTINEL, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("写入固件版本映射负缓存失败: productId={}, version={}, internalVersion={}",
                    productId, version, internalVersion, e);
        }
    }

    @Override
    public void evict(Long productId, String version, String internalVersion) {
        if (productId == null || !StringUtils.hasText(version)) {
            return;
        }
        try {
            redisTemplate.delete(buildKey(productId, version, internalVersion));
        } catch (Exception e) {
            log.warn("删除固件版本映射缓存失败: productId={}, version={}, internalVersion={}",
                    productId, version, internalVersion, e);
        }
    }

    private String buildKey(Long productId, String version, String internalVersion) {
        String normalizedVersion = encode(version);
        String normalizedTag = StringUtils.hasText(internalVersion) ? encode(internalVersion) : NULL_TAG;
        return String.format(RedisKeyConstants.FIRMWARE_LOOKUP_KEY_TEMPLATE, productId, normalizedVersion, normalizedTag);
    }

    private void renewTtl(String key) {
        try {
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.FIRMWARE_LOOKUP_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("续期固件版本映射缓存 TTL 失败: key={}", key, e);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
