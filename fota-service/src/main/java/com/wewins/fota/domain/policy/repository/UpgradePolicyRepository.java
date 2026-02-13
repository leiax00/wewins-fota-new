package com.wewins.fota.domain.policy.repository;

import com.wewins.fota.domain.policy.entity.UpgradePolicy;

import java.util.List;
import java.util.Optional;

/**
 * 升级策略仓储（领域端口）。
 */
public interface UpgradePolicyRepository {

    Optional<UpgradePolicy> findById(Long id);

    List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId);
}
