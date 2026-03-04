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

    public void invalidateOnFirmwarePublish(Long productId, Long versionId, String tag) {
        String firmwareKey = String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId);
        redisTemplate.delete(firmwareKey);
        
        String firmwareListKey = String.format(RedisKeyConstants.PRODUCT_FIRMWARE_LIST_KEY_TEMPLATE, productId);
        redisTemplate.delete(firmwareListKey);
        
        if (tag != null && !tag.isBlank()) {
            String tagKey = String.format(RedisKeyConstants.FIRMWARE_TAG_INDEX_KEY_TEMPLATE, productId, tag);
            redisTemplate.delete(tagKey);
        }
        
        log.info("固件缓存已失效: productId={}, versionId={}", productId, versionId);
    }

    public void invalidateOnFirmwareTagChange(Long productId, Long versionId, String oldTag, String newTag) {
        if (oldTag != null && !oldTag.isBlank()) {
            String oldTagKey = String.format(RedisKeyConstants.FIRMWARE_TAG_INDEX_KEY_TEMPLATE, productId, oldTag);
            redisTemplate.delete(oldTagKey);
        }
        if (newTag != null && !newTag.isBlank()) {
            String newTagKey = String.format(RedisKeyConstants.FIRMWARE_TAG_INDEX_KEY_TEMPLATE, productId, newTag);
            redisTemplate.delete(newTagKey);
        }
        
        String firmwareKey = String.format(RedisKeyConstants.FIRMWARE_KEY_TEMPLATE, versionId);
        redisTemplate.delete(firmwareKey);
        
        log.info("固件标签索引已失效: productId={}, versionId={}, oldTag={}, newTag={}", 
            productId, versionId, oldTag, newTag);
    }
}
