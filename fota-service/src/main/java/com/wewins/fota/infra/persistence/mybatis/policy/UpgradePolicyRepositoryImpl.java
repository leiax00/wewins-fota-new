package com.wewins.fota.infra.persistence.mybatis.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.infra.persistence.mybatis.policy.mapper.UpgradePolicyMapper;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
    public List<UpgradePolicy> findActiveByProductIdOrderByPriorityDesc(Long productId) {
        if (productId == null) {
            return List.of();
        }

        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicy::getProductId, productId)
                .orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getId);
        return upgradePolicyMapper.selectList(query);
    }
}
