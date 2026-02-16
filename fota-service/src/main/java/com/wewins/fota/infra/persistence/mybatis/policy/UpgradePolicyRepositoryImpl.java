package com.wewins.fota.infra.persistence.mybatis.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.infra.persistence.mybatis.policy.mapper.UpgradePolicyMapper;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UpgradePolicyRepository 的 MyBatis 实现。
 */
@Repository
@RequiredArgsConstructor
public class UpgradePolicyRepositoryImpl implements UpgradePolicyRepository {

    private final UpgradePolicyMapper upgradePolicyMapper;

    @Override
    public Optional<UpgradePolicy> findById(Long id) {
        return Optional.ofNullable(upgradePolicyMapper.selectById(id));
    }

    @Override
    public Long save(UpgradePolicy policy) {
        if (policy == null) {
            return null;
        }

        if (policy.getId() == null) {
            upgradePolicyMapper.insert(policy);
            return policy.getId();
        }

        upgradePolicyMapper.updateById(policy);
        return policy.getId();
    }

    @Override
    public boolean softDeleteById(Long id) {
        return upgradePolicyMapper.deleteById(id) > 0;
    }

    @Override
    public List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId) {
        if (productId == null) {
            return List.of();
        }

        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicy::getProductId, productId)
                .orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getId);
        return upgradePolicyMapper.selectList(query);
    }

    @Override
    public List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId) {
        if (productId == null) {
            return List.of();
        }

        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicy::getProductId, productId)
                .isNull(UpgradePolicy::getDeletedAt)
                .orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getId);
        return upgradePolicyMapper.selectList(query);
    }

    @Override
    public List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt() {
        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.isNull(UpgradePolicy::getDeletedAt)
                .orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getUpdatedAt);
        return upgradePolicyMapper.selectList(query);
    }

    @Override
    public LocalDateTime findLatestUpdatedAt() {
        UpgradePolicy latest = upgradePolicyMapper.selectOne(
                new LambdaQueryWrapper<UpgradePolicy>()
                        .isNull(UpgradePolicy::getDeletedAt)
                        .orderByDesc(UpgradePolicy::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return latest == null ? null : latest.getUpdatedAt();
    }
}
