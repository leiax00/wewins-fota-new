package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.infra.persistence.converter.UpgradePolicyConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicyMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyPO;
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

    private final UpgradePolicyConverter upgradePolicyConverter;

    @Override
    public Optional<UpgradePolicy> findById(Long id) {
        return Optional.ofNullable(upgradePolicyConverter.toDomain(upgradePolicyMapper.selectById(id)));
    }

    @Override
    public Page<UpgradePolicy> pagePolicies(Page<UpgradePolicy> page, Long productId, String name, String status) {
        LambdaQueryWrapper<UpgradePolicyPO> queryWrapper = new LambdaQueryWrapper<UpgradePolicyPO>()
                .isNull(UpgradePolicyPO::getDeletedAt);

        if (productId != null) {
            queryWrapper.eq(UpgradePolicyPO::getProductId, productId);
        }
        if (StringUtils.hasText(name)) {
            queryWrapper.like(UpgradePolicyPO::getName, name);
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq(UpgradePolicyPO::getStatus, status.toUpperCase());
        }

        queryWrapper.orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getUpdatedAt);

        Page<UpgradePolicyPO> poPage = new Page<>(page.getCurrent(), page.getSize());
        Page<UpgradePolicyPO> queried = upgradePolicyMapper.selectPage(poPage, queryWrapper);
        Page<UpgradePolicy> result = new Page<>(queried.getCurrent(), queried.getSize(), queried.getTotal());
        result.setRecords(upgradePolicyConverter.toDomainList(queried.getRecords()));
        return result;
    }

    @Override
    public UpgradePolicy create(UpgradePolicy policy) {
        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        upgradePolicyMapper.insert(po);
        return upgradePolicyConverter.toDomain(po);
    }

    @Override
    public UpgradePolicy updateById(UpgradePolicy policy) {
        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        upgradePolicyMapper.updateById(po);
        return upgradePolicyConverter.toDomain(po);
    }

    @Override
    public UpgradePolicy updateWithStatusCheck(Long id, PolicyStatus expectedStatus, UpgradePolicy policy) {
        // 使用 MyBatis-Plus 的 LambdaQueryWrapper 实现带状态校验的 UPDATE
        LambdaQueryWrapper<UpgradePolicyPO> queryWrapper = new LambdaQueryWrapper<UpgradePolicyPO>()
                .eq(UpgradePolicyPO::getId, id)
                .eq(UpgradePolicyPO::getStatus, expectedStatus)
                .isNull(UpgradePolicyPO::getDeletedAt);

        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        int updated = upgradePolicyMapper.update(po, queryWrapper);

        if (updated == 0) {
            // 状态不匹配或记录不存在，返回 null 表示更新失败
            return null;
        }

        return upgradePolicyConverter.toDomain(po);
    }

    @Override
    public boolean deleteById(Long id) {
        return upgradePolicyMapper.deleteById(id) > 0;
    }

    @Override
    public long countByProductIdAndNameExcludingId(Long productId, String name, Long excludeId) {
        LambdaQueryWrapper<UpgradePolicyPO> queryWrapper = new LambdaQueryWrapper<UpgradePolicyPO>()
                .eq(UpgradePolicyPO::getProductId, productId)
                .eq(UpgradePolicyPO::getName, name)
                .isNull(UpgradePolicyPO::getDeletedAt);

        if (excludeId != null) {
            queryWrapper.ne(UpgradePolicyPO::getId, excludeId);
        }

        return upgradePolicyMapper.selectCount(queryWrapper);
    }

    @Override
    public List<UpgradePolicy> findByProductIdOrderByPriorityDesc(Long productId) {
        if (productId == null) {
            return List.of();
        }
        List<UpgradePolicyPO> policies = upgradePolicyMapper.selectList(new LambdaQueryWrapper<UpgradePolicyPO>()
                .eq(UpgradePolicyPO::getProductId, productId)
                .isNull(UpgradePolicyPO::getDeletedAt)
                .orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getId));
        return upgradePolicyConverter.toDomainList(policies);
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

        LambdaQueryWrapper<UpgradePolicyPO> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicyPO::getProductId, productId)
                .isNull(UpgradePolicyPO::getDeletedAt);

        if (includeTestPolicies) {
            query.in(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE, PolicyStatus.VERIFIED, PolicyStatus.TESTING);
        } else {
            query.eq(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE);
        }

        query.orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getId);

        List<UpgradePolicyPO> policyPos = upgradePolicyMapper.selectList(query);
        List<UpgradePolicy> policies = upgradePolicyConverter.toDomainList(policyPos);

        policyCacheRepository.cacheProductPolicies(productId, includeTestPolicies, policies);
        log.debug("策略缓存已写入: productId={}, includeTest={}, count={}", productId, includeTestPolicies, policies.size());

        return policies;
    }

    @Override
    public List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt() {
        LambdaQueryWrapper<UpgradePolicyPO> query = new LambdaQueryWrapper<>();
        query.isNull(UpgradePolicyPO::getDeletedAt)
                .eq(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE)
                .orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getUpdatedAt);
        return upgradePolicyConverter.toDomainList(upgradePolicyMapper.selectList(query));
    }

    @Override
    public LocalDateTime findLatestUpdatedAt() {
        UpgradePolicyPO latest = upgradePolicyMapper.selectOne(
                new LambdaQueryWrapper<UpgradePolicyPO>()
                        .isNull(UpgradePolicyPO::getDeletedAt)
                        .orderByDesc(UpgradePolicyPO::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return latest == null ? null : latest.getUpdatedAt();
    }
}
