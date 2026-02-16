package com.wewins.fota.domain.policy.repository;

import com.wewins.fota.domain.policy.entity.UpgradePolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 升级策略仓储（领域端口）。
 */
public interface UpgradePolicyRepository {

    Optional<UpgradePolicy> findById(Long id);

    Long save(UpgradePolicy policy);

    boolean softDeleteById(Long id);

    List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt();

    LocalDateTime findLatestUpdatedAt();
}
