package com.wewins.fota.domain.policy.cache;

import com.wewins.fota.domain.policy.entity.UpgradePolicy;

import java.util.List;

/**
 * 策略缓存仓储（领域端口）。
 */
public interface PolicyCacheRepository {

    /**
     * 获取产品的策略列表缓存
     *
     * @param productId         产品 ID
     * @param includeTestPolicies 是否包含测试策略
     * @return 策略列表，缓存未命中返回 null
     */
    List<UpgradePolicy> getProductPolicies(Long productId, boolean includeTestPolicies);

    /**
     * 缓存产品的策略列表
     *
     * @param productId         产品 ID
     * @param includeTestPolicies 是否包含测试策略
     * @param policies          策略列表
     */
    void cacheProductPolicies(Long productId, boolean includeTestPolicies, List<UpgradePolicy> policies);

    /**
     * 失效产品的策略列表缓存
     *
     * @param productId 产品 ID
     */
    void evictProductPolicies(Long productId);

    /**
     * 失效单个策略缓存
     *
     * @param policyId 策略 ID
     */
    void evictPolicy(Long policyId);

    /**
     * 失效所有策略相关缓存
     */
    void evictAll();
}
