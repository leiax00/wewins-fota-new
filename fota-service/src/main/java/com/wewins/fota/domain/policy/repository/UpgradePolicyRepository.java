package com.wewins.fota.domain.policy.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 升级策略仓储（领域端口）。
 */
public interface UpgradePolicyRepository {

    Optional<UpgradePolicy> findById(Long id);

    Page<UpgradePolicy> pagePolicies(Page<UpgradePolicy> page, Long productId, String name, String status);

    UpgradePolicy create(UpgradePolicy policy);

    UpgradePolicy updateById(UpgradePolicy policy);

    boolean deleteById(Long id);

    long countByProductIdAndNameExcludingId(Long productId, String name, Long excludeId);

    List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt();

    LocalDateTime findLatestUpdatedAt();
}
