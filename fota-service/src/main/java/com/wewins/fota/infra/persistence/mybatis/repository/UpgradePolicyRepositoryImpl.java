package com.wewins.fota.infra.persistence.mybatis.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.repository.PolicyCacheRepository;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.infra.persistence.converter.UpgradePolicyConverter;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicyMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicySourceVersionMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicyTargetBatchMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicyTargetDeviceMapper;
import com.wewins.fota.infra.persistence.mybatis.mapper.UpgradePolicyTargetTagMapper;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyPO;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicySourceVersionPO;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetBatchPO;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetDevicePO;
import com.wewins.fota.infra.persistence.mybatis.po.UpgradePolicyTargetTagPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UpgradePolicyRepositoryImpl implements UpgradePolicyRepository {

    private final UpgradePolicyMapper upgradePolicyMapper;
    private final PolicyCacheRepository policyCacheRepository;
    private final UpgradePolicyConverter upgradePolicyConverter;
    private final UpgradePolicySourceVersionMapper upgradePolicySourceVersionMapper;
    private final UpgradePolicyTargetDeviceMapper upgradePolicyTargetDeviceMapper;
    private final UpgradePolicyTargetBatchMapper upgradePolicyTargetBatchMapper;
    private final UpgradePolicyTargetTagMapper upgradePolicyTargetTagMapper;

    @Override
    public Optional<UpgradePolicy> findById(Long id) {
        return Optional.ofNullable(enrichPolicy(upgradePolicyConverter.toDomain(upgradePolicyMapper.selectById(id))));
    }

    @Override
    public Page<UpgradePolicy> pagePolicies(Page<UpgradePolicy> page, Long productId, String name, String status) {
        LambdaQueryWrapper<UpgradePolicyPO> queryWrapper = new LambdaQueryWrapper<UpgradePolicyPO>()
                .eq(UpgradePolicyPO::getDeleted, 0);

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
        result.setRecords(enrichPolicies(upgradePolicyConverter.toDomainList(queried.getRecords())));
        return result;
    }

    @Override
    public UpgradePolicy create(UpgradePolicy policy) {
        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        upgradePolicyMapper.insert(po);
        policy.setId(po.getId());
        policy.setCreatedAt(po.getCreatedAt());
        policy.setCreatedBy(po.getCreatedBy());
        policy.setUpdatedAt(po.getUpdatedAt());
        policy.setUpdatedBy(po.getUpdatedBy());
        syncRelations(policy);
        return enrichPolicy(policy);
    }

    @Override
    public UpgradePolicy updateById(UpgradePolicy policy) {
        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        upgradePolicyMapper.updateById(po);
        policy.setUpdatedAt(po.getUpdatedAt());
        policy.setUpdatedBy(po.getUpdatedBy());
        syncRelations(policy);
        return enrichPolicy(policy);
    }

    @Override
    public UpgradePolicy updateWithStatusCheck(Long id, PolicyStatus expectedStatus, UpgradePolicy policy) {
        LambdaQueryWrapper<UpgradePolicyPO> queryWrapper = new LambdaQueryWrapper<UpgradePolicyPO>()
                .eq(UpgradePolicyPO::getId, id)
                .eq(UpgradePolicyPO::getStatus, expectedStatus)
                .eq(UpgradePolicyPO::getDeleted, 0);

        UpgradePolicyPO po = upgradePolicyConverter.toPo(policy);
        int updated = upgradePolicyMapper.update(po, queryWrapper);

        if (updated == 0) {
            return null;
        }

        policy.setUpdatedAt(po.getUpdatedAt());
        policy.setUpdatedBy(po.getUpdatedBy());
        syncRelations(policy);
        return enrichPolicy(policy);
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
                .eq(UpgradePolicyPO::getDeleted, 0);

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
                .eq(UpgradePolicyPO::getDeleted, 0)
                .orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getId));
        return enrichPolicies(upgradePolicyConverter.toDomainList(policies));
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
                .eq(UpgradePolicyPO::getDeleted, 0);

        if (includeTestPolicies) {
            query.in(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE, PolicyStatus.VERIFIED, PolicyStatus.TESTING);
        } else {
            query.eq(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE);
        }

        query.orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getId);

        List<UpgradePolicyPO> policyPos = upgradePolicyMapper.selectList(query);
        List<UpgradePolicy> policies = enrichPolicies(upgradePolicyConverter.toDomainList(policyPos));

        policyCacheRepository.cacheProductPolicies(productId, includeTestPolicies, policies);
        log.debug("策略缓存已写入: productId={}, includeTest={}, count={}", productId, includeTestPolicies, policies.size());

        return policies;
    }

    @Override
    public List<UpgradePolicy> findAllActiveOrderByPriorityAndUpdatedAt() {
        LambdaQueryWrapper<UpgradePolicyPO> query = new LambdaQueryWrapper<>();
        query.eq(UpgradePolicyPO::getDeleted, 0)
                .eq(UpgradePolicyPO::getStatus, PolicyStatus.ACTIVE)
                .orderByDesc(UpgradePolicyPO::getPriority)
                .orderByDesc(UpgradePolicyPO::getUpdatedAt);
        return enrichPolicies(upgradePolicyConverter.toDomainList(upgradePolicyMapper.selectList(query)));
    }

    @Override
    public LocalDateTime findLatestUpdatedAt() {
        UpgradePolicyPO latest = upgradePolicyMapper.selectOne(
                new LambdaQueryWrapper<UpgradePolicyPO>()
                        .eq(UpgradePolicyPO::getDeleted, 0)
                        .orderByDesc(UpgradePolicyPO::getUpdatedAt)
                        .last("LIMIT 1")
        );
        return latest == null ? null : latest.getUpdatedAt();
    }

    private List<UpgradePolicy> enrichPolicies(List<UpgradePolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return policies;
        }
        List<Long> ids = policies.stream().map(UpgradePolicy::getId).filter(id -> id != null).toList();
        if (ids.isEmpty()) {
            return policies;
        }

        Map<Long, java.util.Set<Long>> sourceVersionsByPolicy = upgradePolicySourceVersionMapper.selectByPolicyIds(ids).stream()
                .collect(Collectors.groupingBy(
                        UpgradePolicySourceVersionPO::getPolicyId,
                        LinkedHashMap::new,
                        Collectors.mapping(UpgradePolicySourceVersionPO::getSourceVersionId, Collectors.toCollection(LinkedHashSet::new))
                ));
        Map<Long, java.util.Set<String>> targetDevicesByPolicy = upgradePolicyTargetDeviceMapper.selectByPolicyIds(ids).stream()
                .collect(Collectors.groupingBy(
                        UpgradePolicyTargetDevicePO::getPolicyId,
                        LinkedHashMap::new,
                        Collectors.mapping(UpgradePolicyTargetDevicePO::getImei, Collectors.toCollection(LinkedHashSet::new))
                ));
        Map<Long, java.util.Set<Long>> targetBatchesByPolicy = upgradePolicyTargetBatchMapper.selectByPolicyIds(ids).stream()
                .collect(Collectors.groupingBy(
                        UpgradePolicyTargetBatchPO::getPolicyId,
                        LinkedHashMap::new,
                        Collectors.mapping(UpgradePolicyTargetBatchPO::getBatchId, Collectors.toCollection(LinkedHashSet::new))
                ));
        Map<Long, Map<String, Object>> targetTagsByPolicy = upgradePolicyTargetTagMapper.selectByPolicyIds(ids).stream()
                .collect(Collectors.groupingBy(
                        UpgradePolicyTargetTagPO::getPolicyId,
                        LinkedHashMap::new,
                        Collectors.toMap(UpgradePolicyTargetTagPO::getTagKey, row -> row.getTagValue(), (a, b) -> b, LinkedHashMap::new)
                ));

        policies.forEach(policy -> {
            java.util.Set<Long> sourceVersions = sourceVersionsByPolicy.get(policy.getId());
            if (sourceVersions != null && !sourceVersions.isEmpty()) {
                policy.setSourceVersions(sourceVersions);
            }
            java.util.Set<String> targetDevices = targetDevicesByPolicy.get(policy.getId());
            if (targetDevices != null && !targetDevices.isEmpty()) {
                policy.setTargetImeis(targetDevices);
            }
            java.util.Set<Long> targetBatches = targetBatchesByPolicy.get(policy.getId());
            if (targetBatches != null && !targetBatches.isEmpty()) {
                policy.setTargetDeviceBatchIds(targetBatches);
            }
            Map<String, Object> targetTags = targetTagsByPolicy.get(policy.getId());
            if (targetTags != null && !targetTags.isEmpty()) {
                policy.setTargetDeviceTags(targetTags);
            }
        });

        return policies;
    }

    private UpgradePolicy enrichPolicy(UpgradePolicy policy) {
        if (policy == null || policy.getId() == null) {
            return policy;
        }
        return enrichPolicies(new java.util.ArrayList<>(List.of(policy))).getFirst();
    }

    private void syncRelations(UpgradePolicy policy) {
        if (policy == null || policy.getId() == null) {
            return;
        }
        Long policyId = policy.getId();

        upgradePolicySourceVersionMapper.deleteByPolicyIds(List.of(policyId));
        if (policy.getSourceVersions() != null && !policy.getSourceVersions().isEmpty()) {
            upgradePolicySourceVersionMapper.batchInsert(policy.getSourceVersions().stream()
                    .map(sourceVersionId -> {
                        UpgradePolicySourceVersionPO row = new UpgradePolicySourceVersionPO();
                        row.setPolicyId(policyId);
                        row.setSourceVersionId(sourceVersionId);
                        return row;
                    }).toList());
        }

        upgradePolicyTargetDeviceMapper.deleteByPolicyIds(List.of(policyId));
        if (policy.getTargetImeis() != null && !policy.getTargetImeis().isEmpty()) {
            upgradePolicyTargetDeviceMapper.batchInsert(policy.getTargetImeis().stream()
                    .map(imei -> {
                        UpgradePolicyTargetDevicePO row = new UpgradePolicyTargetDevicePO();
                        row.setPolicyId(policyId);
                        row.setImei(imei);
                        return row;
                    }).toList());
        }

        upgradePolicyTargetBatchMapper.deleteByPolicyIds(List.of(policyId));
        if (policy.getTargetDeviceBatchIds() != null && !policy.getTargetDeviceBatchIds().isEmpty()) {
            upgradePolicyTargetBatchMapper.batchInsert(policy.getTargetDeviceBatchIds().stream()
                    .map(batchId -> {
                        UpgradePolicyTargetBatchPO row = new UpgradePolicyTargetBatchPO();
                        row.setPolicyId(policyId);
                        row.setBatchId(batchId);
                        return row;
                    }).toList());
        }

        upgradePolicyTargetTagMapper.deleteByPolicyIds(List.of(policyId));
        if (policy.getTargetDeviceTags() != null && !policy.getTargetDeviceTags().isEmpty()) {
            upgradePolicyTargetTagMapper.batchInsert(policy.getTargetDeviceTags().entrySet().stream()
                    .map(entry -> {
                        UpgradePolicyTargetTagPO row = new UpgradePolicyTargetTagPO();
                        row.setPolicyId(policyId);
                        row.setTagKey(entry.getKey());
                        row.setTagValue(String.valueOf(entry.getValue()));
                        row.setOperator("EQ");
                        return row;
                    }).toList());
        }
    }
}
