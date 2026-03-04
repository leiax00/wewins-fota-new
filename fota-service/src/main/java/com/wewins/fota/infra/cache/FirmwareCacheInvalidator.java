package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirmwareCacheInvalidator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheIndexService cacheIndexService;

    public void invalidateOnFirmwarePublish(Long productId, Long versionId, String tag) {
        String firmwareKey = String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId);
        redisTemplate.delete(firmwareKey);
        
        if (tag != null && !tag.isBlank()) {
            String tagKey = String.format(RedisKeyConstants.FIRMWARE_TAG_INDEX_KEY_TEMPLATE, productId, tag);
            redisTemplate.delete(tagKey);
        }
        
        cacheIndexService.invalidateFirmwareCache(versionId);
        
        log.info("固件缓存已失效: productId={}, versionId={}", productId, versionId);
    }
}
