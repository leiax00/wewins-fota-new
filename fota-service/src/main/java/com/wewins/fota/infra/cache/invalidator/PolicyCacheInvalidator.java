package com.wewins.fota.infra.cache.invalidator;

import com.wewins.fota.cache.constant.RedisKeyConstants;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.infra.cache.service.CacheIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyCacheInvalidator {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PolicyCacheRepository policyCacheRepository;
    private final CacheIndexService cacheIndexService;

    public void invalidateOnPolicyChange(Long productId, Long policyId) {
        String policyKey = String.format(RedisKeyConstants.POLICY_KEY_TEMPLATE, policyId);
        redisTemplate.delete(policyKey);
        
        String allPoliciesKey = String.format(RedisKeyConstants.PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "all");
        String prodPoliciesKey = String.format(RedisKeyConstants.PRODUCT_POLICY_LIST_KEY_TEMPLATE, productId, "prod");
        redisTemplate.delete(List.of(allPoliciesKey, prodPoliciesKey));
        
        cacheIndexService.invalidatePolicyCache(policyId);
        
        log.info("策略缓存已失效: productId={}, policyId={}", productId, policyId);
    }

    public void invalidateOnBatchPolicyChange(Long productId) {
        policyCacheRepository.evictProductPolicies(productId);
        cacheIndexService.invalidateProductPolicyCache(productId);
        log.warn("产品所有策略缓存已失效: productId={}", productId);
    }
}
