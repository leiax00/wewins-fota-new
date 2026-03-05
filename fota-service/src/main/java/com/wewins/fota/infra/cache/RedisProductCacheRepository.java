package com.wewins.fota.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.cache.CacheLookupResult;
import com.wewins.fota.domain.product.cache.ProductCacheRepository;
import com.wewins.fota.domain.product.entity.Product;
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
public class RedisProductCacheRepository implements ProductCacheRepository {

    private static final String MODEL_NOT_FOUND_SENTINEL = "NF";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetricsService cacheMetricsService;

    @Override
    public Optional<Product> findById(Long productId) {
        try {
            String key = buildProductKey(productId);
            Object cached = redisTemplate.opsForValue().get(key);
            
            if (cached != null) {
                cacheMetricsService.recordProductCacheHit();
                renewTtl(key);
                return Optional.ofNullable(deserializeProduct(cached.toString()));
            }
            cacheMetricsService.recordProductCacheMiss();
        } catch (Exception e) {
            log.error("从 Redis 获取产品缓存失败: productId={}", productId, e);
            cacheMetricsService.recordProductCacheMiss();
        }
        return Optional.empty();
    }

    @Override
    public CacheLookupResult<Long> getModelLookup(String model) {
        try {
            String key = buildProductModelIndexKey(model);
            Object cached = redisTemplate.opsForValue().get(key);
            
            if (cached != null) {
                cacheMetricsService.recordProductCacheHit();
                String value = cached.toString();
                if (MODEL_NOT_FOUND_SENTINEL.equals(value)) {
                    return CacheLookupResult.hitNotFound();
                }
                renewTtl(key);
                return CacheLookupResult.hit(Long.parseLong(value));
            }
            cacheMetricsService.recordProductCacheMiss();
        } catch (Exception e) {
            log.error("从 Redis 获取产品型号索引失败: model={}", model, e);
            cacheMetricsService.recordProductCacheMiss();
        }
        return CacheLookupResult.miss();
    }

    @Override
    public void cacheProduct(Product product) {
        try {
            String key = buildProductKey(product.getId());
            String json = serializeProduct(product);
            
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.PRODUCT_CACHE_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
            
            log.debug("产品缓存已更新: productId={}", product.getId());
        } catch (Exception e) {
            log.error("写入 Redis 产品缓存失败: productId={}", product.getId(), e);
        }
    }

    @Override
    public void cacheProductByModel(String model, Long productId) {
        try {
            String key = buildProductModelIndexKey(model);
            
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.PRODUCT_CACHE_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, productId.toString(), Duration.ofSeconds(ttl));
            
            log.debug("产品型号索引已更新: model={}, productId={}", model, productId);
        } catch (Exception e) {
            log.error("写入 Redis 产品型号索引失败: model={}", model, e);
        }
    }

    @Override
    public void cacheModelNotFound(String model) {
        try {
            String key = buildProductModelIndexKey(model);
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.PRODUCT_MODEL_NOT_FOUND_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, MODEL_NOT_FOUND_SENTINEL, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.error("写入产品型号负缓存失败: model={}", model, e);
        }
    }

    @Override
    public void evict(Long productId) {
        try {
            String key = buildProductKey(productId);
            redisTemplate.delete(key);
            log.debug("产品缓存已删除: productId={}", productId);
        } catch (Exception e) {
            log.error("删除 Redis 产品缓存失败: productId={}", productId, e);
        }
    }

    @Override
    public void evictByModel(String model) {
        try {
            String key = buildProductModelIndexKey(model);
            redisTemplate.delete(key);
            log.debug("产品型号索引已删除: model={}", model);
        } catch (Exception e) {
            log.error("删除 Redis 产品型号索引失败: model={}", model, e);
        }
    }

    private String buildProductKey(Long productId) {
        return String.format(RedisKeyConstants.PRODUCT_KEY_TEMPLATE, productId);
    }

    private String buildProductModelIndexKey(String model) {
        return String.format(RedisKeyConstants.PRODUCT_MODEL_INDEX_KEY_TEMPLATE, model);
    }

    private void renewTtl(String key) {
        try {
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.PRODUCT_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("续期产品缓存 TTL 失败: key={}", key, e);
        }
    }

    private String serializeProduct(Product product) throws JsonProcessingException {
        return objectMapper.writeValueAsString(product);
    }

    private Product deserializeProduct(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化产品缓存失败", e);
            return null;
        }
    }
}
