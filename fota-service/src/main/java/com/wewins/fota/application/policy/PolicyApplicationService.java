package com.wewins.fota.application.policy;

import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.infra.persistence.mybatis.firmware.mapper.FirmwareVersionMapper;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.infra.persistence.mybatis.policy.mapper.UpgradePolicyMapper;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.infra.persistence.mybatis.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略应用服务
 * <p>
 * 跨领域编排服务，负责升级策略的 CRUD 和查询
 * 协调策略、产品、固件等多个领域
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyApplicationService {

    private final UpgradePolicyMapper upgradePolicyMapper;
    private final ProductMapper productMapper;
    private final FirmwareVersionMapper firmwareVersionMapper;

    /**
     * 创建升级策略
     *
     * @param policy 策略实体
     * @return 创建的策略 ID
     */
    public Long createPolicy(UpgradePolicy policy) {
        validatePolicyForCreate(policy);

        policy.setCreatedAt(LocalDateTime.now());
        policy.setUpdatedAt(LocalDateTime.now());

        upgradePolicyMapper.insert(policy);

        log.info("升级策略创建成功: policyId={}, name={}",
                policy.getId(), policy.getName());

        return policy.getId();
    }

    /**
     * 更新升级策略
     *
     * @param policy 策略实体
     * @return 更新的行数
     */
    public int updatePolicy(UpgradePolicy policy) {
        validatePolicyForUpdate(policy);

        policy.setUpdatedAt(LocalDateTime.now());

        int rows = upgradePolicyMapper.updateById(policy);

        log.info("升级策略更新成功: policyId={}, rowsAffected={}",
                policy.getId(), rows);

        return rows;
    }

    /**
     * 删除升级策略（软删除）
     *
     * @param policyId 策略 ID
     */
    public void deletePolicy(Long policyId) {
        UpgradePolicy policy = upgradePolicyMapper.selectById(policyId);
        if (policy == null) {
            log.warn("策略不存在，无法删除: policyId={}", policyId);
            return;
        }

        policy.setDeletedAt(LocalDateTime.now());
        upgradePolicyMapper.updateById(policy);

        log.info("升级策略删除成功: policyId={}, name={}",
                policyId, policy.getName());
    }

    /**
     * 根据产品 ID 查询策略列表
     *
     * @param productId 产品 ID
     * @return 策略列表
     */
    public List<UpgradePolicy> getPoliciesByProduct(Long productId) {
        List<UpgradePolicy> policies = upgradePolicyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UpgradePolicy>()
                        .eq(UpgradePolicy::getProductId, productId)
                        .eq(UpgradePolicy::getDeletedAt, null)
                        .orderByDesc(UpgradePolicy::getPriority)
        );

        log.debug("查询产品策略列表: productId={}, count={}", productId, policies.size());

        return policies;
    }

    /**
     * 获取策略详情
     *
     * @param policyId 策略 ID
     * @return 策略实体
     */
    public UpgradePolicy getPolicy(Long policyId) {
        UpgradePolicy policy = upgradePolicyMapper.selectById(policyId);

        if (policy == null || policy.getDeletedAt() != null) {
            log.warn("策略不存在或已删除: policyId={}", policyId);
            return null;
        }

        log.debug("获取策略详情: policyId={}, name={}", policyId, policy.getName());

        return policy;
    }

    /**
     * 激活升级策略
     *
     * @param policyId 策略 ID
     */
    public void activatePolicy(Long policyId) {
        UpgradePolicy policy = getPolicy(policyId);
        if (policy == null) {
            log.warn("策略不存在，无法激活: policyId={}", policyId);
            return;
        }

        // TODO: 添加激活逻辑（更新状态、发送通知等）
        log.info("升级策略激活: policyId={}, name={}", policyId, policy.getName());
    }

    /**
     * 暂停升级策略
     *
     * @param policyId 策略 ID
     */
    public void deactivatePolicy(Long policyId) {
        UpgradePolicy policy = getPolicy(policyId);
        if (policy == null) {
            log.warn("策略不存在，无法暂停: policyId={}", policyId);
            return;
        }

        // TODO: 添加暂停逻辑（更新状态、清理配额等）
        log.info("升级策略暂停: policyId={}, name={}", policyId, policy.getName());
    }

    /**
     * 验证策略创建的合法性
     *
     * @param policy 策略实体
     */
    private void validatePolicyForCreate(UpgradePolicy policy) {
        if (policy.getProductId() == null) {
            throw new IllegalArgumentException("产品 ID 不能为空");
        }

        if (policy.getTargetVersionId() == null) {
            throw new IllegalArgumentException("目标版本 ID 不能为空");
        }

        // 验证产品存在
        Product product = productMapper.selectById(policy.getProductId());
        if (product == null || product.getDeletedAt() != null) {
            throw new IllegalArgumentException("产品不存在或已删除");
        }

        // 验证目标版本存在
        FirmwareVersion version = firmwareVersionMapper.selectById(policy.getTargetVersionId());
        if (version == null || version.getDeletedAt() != null) {
            throw new IllegalArgumentException("目标固件版本不存在或已删除");
        }
    }

    /**
     * 验证策略更新的合法性
     *
     * @param policy 策略实体
     */
    private void validatePolicyForUpdate(UpgradePolicy policy) {
        if (policy.getId() == null) {
            throw new IllegalArgumentException("策略 ID 不能为空");
        }

        // 验证策略存在
        UpgradePolicy existing = upgradePolicyMapper.selectById(policy.getId());
        if (existing == null || existing.getDeletedAt() != null) {
            throw new IllegalArgumentException("策略不存在或已删除");
        }
    }
}
