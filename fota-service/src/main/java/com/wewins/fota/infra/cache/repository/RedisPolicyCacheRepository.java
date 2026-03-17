package com.wewins.fota.infra.cache.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.infra.cache.service.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisPolicyCacheRepository implements PolicyCacheRepository {

    private static final String EMPTY_POLICY_SENTINEL = "__EMPTY__";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheMetricsService cacheMetricsService;

    @Override
    public List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTestPolicies) {
        try {
            String key = buildProductPolicyListKey(productId, includeTestPolicies);
            Set<Object> policyIds = redisTemplate.opsForSet().members(key);

            if (policyIds != null && !policyIds.isEmpty()) {
                if (policyIds.size() == 1 && policyIds.contains(EMPTY_POLICY_SENTINEL)) {
                    cacheMetricsService.recordPolicyCacheHit();
                    return List.of();
                }

                List<String> policyKeys = new ArrayList<>(policyIds.size());
                for (Object policyId : policyIds) {
                    policyKeys.add(String.format(RedisKeyConstants.POLICY_KEY_TEMPLATE, policyId));
                }

                List<Object> cachedPolicies = redisTemplate.opsForValue().multiGet(policyKeys);
                List<UpgradePolicy> policies = new ArrayList<>();
                boolean hasMissingPolicy = false;
                if (cachedPolicies != null) {
                    for (int i = 0; i < cachedPolicies.size(); i++) {
                        String policyKey = policyKeys.get(i);
                        Object cachedPolicy = cachedPolicies.get(i);
                        if (cachedPolicy == null) {
                            hasMissingPolicy = true;
                            continue;
                        }

                        UpgradePolicy policy = deserializePolicy(cachedPolicy.toString());
                        if (policy != null) {
                            policies.add(policy);
                            renewTtl(policyKey);
                        } else {
                            hasMissingPolicy = true;
                        }
                    }
                }

                if (hasMissingPolicy) {
                    redisTemplate.delete(key);
                    cacheMetricsService.recordPolicyCacheMiss();
                    return null;
                }

                renewTtl(key);
                cacheMetricsService.recordPolicyCacheHit();
                return policies;
            }
            cacheMetricsService.recordPolicyCacheMiss();
        } catch (Exception e) {
            log.error("从 Redis 获取策略缓存失败: productId={}, includeTest={}", productId, includeTestPolicies, e);
            cacheMetricsService.recordPolicyCacheMiss();
        }
        return null;
    }

    @Override
    public void cacheProductPolicies(Long productId, boolean includeTestPolicies, List<UpgradePolicy> policies) {
        try {
            String key = buildProductPolicyListKey(productId, includeTestPolicies);
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_CACHE_TTL_SECONDS);

            Set<Object> policyIds = new HashSet<>();
            for (UpgradePolicy policy : policies) {
                if (policy == null || policy.getId() == null) {
                    continue;
                }

                String policyKey = String.format(RedisKeyConstants.POLICY_KEY_TEMPLATE, policy.getId());
                String policyJson = serializePolicy(policy);
                redisTemplate.opsForValue().set(policyKey, policyJson, Duration.ofSeconds(ttl));
                policyIds.add(policy.getId());
            }

            redisTemplate.delete(key);
            if (!policyIds.isEmpty()) {
                redisTemplate.opsForSet().add(key, policyIds.toArray());
                redisTemplate.expire(key, Duration.ofSeconds(ttl));
            } else {
                long emptyTtl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.POLICY_EMPTY_CACHE_TTL_SECONDS);
                redisTemplate.opsForSet().add(key, EMPTY_POLICY_SENTINEL);
                redisTemplate.expire(key, Duration.ofSeconds(emptyTtl));
            }
            
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
            Set<String> keys = scanKeys("fota:cache:list:product:policy:*");
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("所有策略缓存已失效: count={}", keys.size());
            }
            
            Set<String> indexKeys = scanKeys("fota:cache:index:product:*");
            if (!indexKeys.isEmpty()) {
                redisTemplate.delete(indexKeys);
            }
        } catch (Exception e) {
            log.error("失效所有策略缓存失败", e);
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new java.util.HashSet<>();
        try (var cursor = redisTemplate.scan(org.springframework.data.redis.core.ScanOptions.scanOptions().match(pattern).count(1000).build())) {
            cursor.forEachRemaining(key -> keys.add(key.toString()));
        }
        return keys;
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

    private String serializePolicy(UpgradePolicy policy) throws JsonProcessingException {
        return objectMapper.writeValueAsString(policy);
    }

    private UpgradePolicy deserializePolicy(String json) {
        try {
            return objectMapper.readValue(json, UpgradePolicy.class);
        } catch (JsonProcessingException e) {
            log.error("反序列化策略缓存失败", e);
            return null;
        }
    }
}
