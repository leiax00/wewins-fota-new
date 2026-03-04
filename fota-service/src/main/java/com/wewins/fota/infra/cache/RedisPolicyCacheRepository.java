package com.wewins.fota.infra.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.policy.cache.PolicyCacheRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisPolicyCacheRepository implements PolicyCacheRepository {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTestPolicies) {
        try {
            String key = buildProductPolicyListKey(productId, includeTestPolicies);
            Object cached = redisTemplate.opsForValue().get(key);
            
            if (cached != null) {
                renewTtl(key);
                return deserializePolicies(cached.toString());
            }
        } catch (Exception e) {
            log.error("从 Redis 获取策略缓存失败: productId={}, includeTest={}", productId, includeTestPolicies, e);
        }
        return null;
    }

    @Override
    public void cacheProductPolicies(Long productId, boolean includeTestPolicies, List<UpgradePolicy> policies) {
        try {
            String key = buildProductPolicyListKey(productId, includeTestPolicies);
            String json = serializePolicies(policies);
            
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_CACHE_TTL_SECONDS);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(ttl));
            
            updateCacheIndex(productId, key);
            
            log.debug("策略缓存已更新: productId={}, includeTest={}, count={}", 
                productId, includeTestPolicies, policies.size());
        } catch (Exception e) {
            log.error("写入 Redis 策略缓存失败: productId={}, includeTest={}", productId, includeTestPolicies, e);
        }
    }

    @Override
    public void evictProductPolicies(Long productId) {
        try {
            String indexKey = String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId);
            Set<Object> cachedKeys = redisTemplate.opsForSet().members(indexKey);
            
            if (cachedKeys != null && !cachedKeys.isEmpty()) {
                List<String> keysToDelete = new ArrayList<>();
                for (Object key : cachedKeys) {
                    keysToDelete.add(key.toString());
                }
                redisTemplate.delete(keysToDelete);
                log.info("产品策略缓存已失效: productId={}, count={}", productId, keysToDelete.size());
            }
            
            redisTemplate.delete(indexKey);
        } catch (Exception e) {
            log.error("失效产品策略缓存失败: productId={}", productId, e);
        }
    }

    @Override
    public void evictPolicy(Long policyId) {
        try {
            String policyKey = String.format(RedisKeyConstants.POLICY_KEY_TEMPLATE, policyId);
            redisTemplate.delete(policyKey);
            log.debug("策略缓存已删除: policyId={}", policyId);
        } catch (Exception e) {
            log.error("删除策略缓存失败: policyId={}", policyId, e);
        }
    }

    @Override
    public void evictAll() {
        try {
            Set<String> keys = redisTemplate.keys("fota:cache:product:policy:*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("所有策略缓存已失效: count={}", keys.size());
            }
            
            Set<String> indexKeys = redisTemplate.keys("fota:cache:index:product:*");
            if (!indexKeys.isEmpty()) {
                redisTemplate.delete(indexKeys);
            }
        } catch (Exception e) {
            log.error("失效所有策略缓存失败", e);
        }
    }

    private String buildProductPolicyListKey(Long productId, boolean includeTestPolicies) {
        String type = includeTestPolicies ? "all" : "prod";
        return String.format(RedisKeyConstants.PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, type);
    }

    private void renewTtl(String key) {
        try {
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_CACHE_TTL_SECONDS);
            redisTemplate.expire(key, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("续期策略缓存 TTL 失败: key={}", key, e);
        }
    }

    private void updateCacheIndex(Long productId, String cacheKey) {
        try {
            String indexKey = String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId);
            redisTemplate.opsForSet().add(indexKey, cacheKey);
            
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.CACHE_INDEX_TTL_SECONDS);
            redisTemplate.expire(indexKey, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("更新缓存索引失败: productId={}, key={}", productId, cacheKey, e);
        }
    }

    private String serializePolicies(List<UpgradePolicy> policies) throws JsonProcessingException {
        return objectMapper.writeValueAsString(policies);
    }

    private List<UpgradePolicy> deserializePolicies(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.error("反序列化策略缓存失败", e);
            return null;
        }
    }
}
