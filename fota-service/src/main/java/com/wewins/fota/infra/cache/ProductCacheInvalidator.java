package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheInvalidator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheIndexService cacheIndexService;

    public void invalidateOnProductChange(Long productId) {
        String productKey = String.format(RedisKeyConstants.PRODUCT_KEY_TEMPLATE, productId);
        redisTemplate.delete(productKey);
        
        cacheIndexService.invalidateProductPolicyCache(productId);
        
        String firmwareListKey = String.format(RedisKeyConstants.PRODUCT_FIRMWARE_LIST_KEY_TEMPLATE, productId);
        redisTemplate.delete(firmwareListKey);
        
        log.info("产品缓存已失效: productId={}", productId);
    }

    public void invalidateOnProductStatusChange(Long productId) {
        invalidateOnProductChange(productId);
        log.warn("产品状态变更，缓存已强力失效: productId={}", productId);
    }

    public void invalidateOnProductModelChange(String oldModel, String newModel) {
        if (oldModel != null && !oldModel.isBlank()) {
            String oldKey = String.format(RedisKeyConstants.PRODUCT_MODEL_INDEX_KEY_TEMPLATE, oldModel);
            redisTemplate.delete(oldKey);
        }
        if (newModel != null && !newModel.isBlank()) {
            String newKey = String.format(RedisKeyConstants.PRODUCT_MODEL_INDEX_KEY_TEMPLATE, newModel);
            redisTemplate.delete(newKey);
        }
        log.info("产品型号索引已失效: oldModel={}, newModel={}", oldModel, newModel);
    }
}
