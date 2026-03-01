package com.wewins.fota.domain.policy.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;

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

    /**
     * 带状态校验的策略更新（用于防止状态流转竞态条件）
     *
     * @param id 策略 ID
     * @param expectedStatus 期望的当前状态（UPDATE 的 WHERE 条件）
     * @param policy 要更新的策略实体
     * @return 更新后的策略，如果状态不匹配则返回 null
     */
    UpgradePolicy updateWithStatusCheck(Long id, PolicyStatus expectedStatus, UpgradePolicy policy);

    boolean deleteById(Long id);

    long countByProductIdAndNameExcludingId(Long productId, String name, Long excludeId);

    List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId);

    List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt();

    LocalDateTime findLatestUpdatedAt();
}
