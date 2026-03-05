package com.wewins.fota.infra.cache;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.product.cache.ProductCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCacheInvalidator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheIndexService cacheIndexService;
    private final ProductCacheRepository productCacheRepository;

    public void invalidateOnProductChange(Long productId, String oldModel, String newModel) {
        productCacheRepository.evict(productId);

        if (StringUtils.hasText(oldModel)) {
            productCacheRepository.evictByModel(oldModel);
        }
        if (StringUtils.hasText(newModel) && !newModel.equals(oldModel)) {
            productCacheRepository.evictByModel(newModel);
        }
        
        cacheIndexService.invalidateProductPolicyCache(productId);
        
        log.info("产品缓存已失效: productId={}, oldModel={}, newModel={}", productId, oldModel, newModel);
    }


}
