package com.wewins.fota.infra.cache.service;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.cache.util.RandomizedTtlUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheIndexService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void addToIndex(String indexKey, String cacheKey) {
        try {
            redisTemplate.opsForSet().add(indexKey, cacheKey);
            long ttl = RandomizedTtlUtil.getRandomizedTtl(RedisKeyConstants.CACHE_INDEX_TTL_SECONDS);
            redisTemplate.expire(indexKey, Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("添加缓存索引失败: indexKey={}, cacheKey={}", indexKey, cacheKey, e);
        }
    }

    public void removeFromIndex(String indexKey, String cacheKey) {
        try {
            redisTemplate.opsForSet().remove(indexKey, cacheKey);
        } catch (Exception e) {
            log.warn("移除缓存索引失败: indexKey={}, cacheKey={}", indexKey, cacheKey, e);
        }
    }

    public List<String> getIndexKeys(String indexKey) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(indexKey);
            if (members == null || members.isEmpty()) {
                return List.of();
            }
            List<String> keys = new ArrayList<>();
            for (Object member : members) {
                keys.add(member.toString());
            }
            return keys;
        } catch (Exception e) {
            log.error("获取缓存索引失败: indexKey={}", indexKey, e);
            return List.of();
        }
    }

    public void invalidateByIndex(String indexKey) {
        try {
            List<String> keys = getIndexKeys(indexKey);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("通过索引批量失效缓存: indexKey={}, count={}", indexKey, keys.size());
            }
            redisTemplate.delete(indexKey);
        } catch (Exception e) {
            log.error("通过索引失效缓存失败: indexKey={}", indexKey, e);
        }
    }

    public void invalidateProductPolicyCache(Long productId) {
        String indexKey = String.format(RedisKeyConstants.PRODUCT_CACHE_INDEX_KEY_TEMPLATE, productId);
        invalidateByIndex(indexKey);
    }

    public void invalidatePolicyCache(Long policyId) {
        String indexKey = String.format(RedisKeyConstants.POLICY_CACHE_INDEX_KEY_TEMPLATE, policyId);
        invalidateByIndex(indexKey);
    }

}
