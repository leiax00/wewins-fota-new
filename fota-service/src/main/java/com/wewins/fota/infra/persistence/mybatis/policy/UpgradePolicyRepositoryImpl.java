package com.wewins.fota.infra.persistence.mybatis.policy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.policy.cache.PolicyCacheRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.infra.persistence.mybatis.policy.mapper.UpgradePolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * UpgradePolicyRepository 的 MyBatis 实现。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UpgradePolicyRepositoryImpl implements UpgradePolicyRepository {

    private final UpgradePolicyMapper upgradePolicyMapper;

    private final PolicyCacheRepository policyCacheRepository;

    @Override
    public Optional<UpgradePolicy> findById(Long id) {
        return Optional.ofNullable(upgradePolicyMapper.selectById(id));
    }

    @Override
    public Page<UpgradePolicy> pagePolicies(Page<UpgradePolicy> page, Long productId, String name, String status) {
        LambdaQueryWrapper<UpgradePolicy> queryWrapper = new LambdaQueryWrapper<UpgradePolicy>()
                .isNull(UpgradePolicy::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(UpgradePolicy::getProductId, productId);
        }
        if (StringUtils.hasText(name)) {
            queryWrapper.like(UpgradePolicy::getName, name);
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(UpgradePolicy::getStatus, status.toUpperCase());
        }

        queryWrapper.orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getUpdatedAt);

        return upgradePolicyMapper.selectPage(page, queryWrapper);
    }

    @Override
    public UpgradePolicy create(UpgradePolicy policy) {
        upgradePolicyMapper.insert(policy);
        return policy;
    }

    @Override
    public UpgradePolicy updateById(UpgradePolicy policy) {
        upgradePolicyMapper.updateById(policy);
        return policy;
    }

    @Override
    public UpgradePolicy updateWithStatusCheck(Long id, PolicyStatus expectedStatus, UpgradePolicy policy) {
        // 使用 MyBatis-Plus 的 LambdaQueryWrapper 实现带状态校验的 UPDATE
        LambdaQueryWrapper<UpgradePolicy> queryWrapper = new LambdaQueryWrapper<UpgradePolicy>()
                .eq(UpgradePolicy::getId, id)
                .eq(UpgradePolicy::getStatus, expectedStatus)
                .isNull(UpgradePolicy::getDeletedAt);

        int updated = upgradePolicyMapper.update(policy, queryWrapper);

        if (updated == 0) {
            // 状态不匹配或记录不存在，返回 null 表示更新失败
            return null;
        }

        return policy;
    }

    @Override
    public boolean deleteById(Long id) {
        return upgradePolicyMapper.deleteById(id) > 0;
    }

    @Override
    public long countByProductIdAndNameExcludingId(Long productId, String name, Long excludeId) {
        LambdaQueryWrapper<UpgradePolicy> queryWrapper = new LambdaQueryWrapper<UpgradePolicy>()
                .eq(UpgradePolicy::getProductId, productId)
                .eq(UpgradePolicy::getName, name)
                .isNull(UpgradePolicy::getDeletedAt);

        if (excludeId != null) {
            queryWrapper.ne(UpgradePolicy::getId, excludeId);
        }

        return upgradePolicyMapper.selectCount(queryWrapper);
    }

    @Override
    public List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId) {
        if (productId == null) {
            return List.of();
        }
        return upgradePolicyMapper.selectList(new LambdaQueryWrapper<UpgradePolicy>()
                .eq(UpgradePolicy::getProductId, productId)
                .isNull(UpgradePolicy::getDeletedAt)
                .orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getId));
    }

    @Override
    public List<UpgradePolicy> findEffectiveByProductIdOrderByPriorityDesc(Long productId, boolean includeTestPolicies) {
        if (productId == null) {
            return List.of();
        }

        List<UpgradePolicy> cached = policyCacheRepository.getProductPolicies(productId, includeTestPolicies);
        if (cached != null) {
            log.debug("策略缓存命中: productId={}, includeTest={}", productId, includeTestPolicies);
            return cached;
        }

        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicy::getProductId, productId)
                .isNull(UpgradePolicy::getDeletedAt);

        if (includeTestPolicies) {
            query.in(UpgradePolicy::getStatus, PolicyStatus.ACTIVE, PolicyStatus.VERIFIED, PolicyStatus.TESTING);
        } else {
            query.eq(UpgradePolicy::getStatus, PolicyStatus.ACTIVE);
        }

        query.orderByDesc(UpgradePolicy::getPriority)
                .orderByDesc(UpgradePolicy::getId);

        List<UpgradePolicy> policies = upgradePolicyMapper.selectList(query);

        if (!policies.isEmpty()) {
            policyCacheRepository.cacheProductPolicies(productId, includeTestPolicies, policies);
            log.debug("策略缓存已写入: productId={}, includeTest={}, count={}", productId, includeTestPolicies, policies.size());
        }

        return policies;
    }

    @Override
    public List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt() {
        LambdaQueryWrapper<UpgradePolicy> query = new LambdaQueryWrapper<>();
        query.isNull(UpgradePolicy::getDeletedAt)
                .eq(UpgradePolicy::getStatus, PolicyStatus.ACTIVE)
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
