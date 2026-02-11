package com.wewins.fota.service;

import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.firmware.mapper.FirmwareVersionMapper;
import com.wewins.fota.domain.product.mapper.ProductMapper;
import com.wewins.fota.domain.policy.mapper.UpgradePolicyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 数据完整性校验服务
 * <p>
 * 用于防御性校验，确保关键业务操作不会引用已软删除的记录。
 * </p>
 *
 * <p>
 * 主要场景：
 * <ul>
 *   <li>策略下发前校验产品和固件版本是否有效</li>
 *   <li>设备升级前校验目标固件版本是否有效</li>
 *   <li>导入设备时校验产品是否存在</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataIntegrityService {

    private final ProductMapper productMapper;
    private final FirmwareVersionMapper firmwareVersionMapper;
    private final UpgradePolicyMapper upgradePolicyMapper;

    /**
     * 检查产品是否活跃（未被软删除）
     *
     * @param productId 产品ID
     * @return true 如果产品存在且未被删除
     */
    public boolean isProductActive(Long productId) {
        if (productId == null) {
            return false;
        }

        Product product = productMapper.selectById(productId);
        boolean isActive = product != null && product.getDeletedAt() == null;

        if (!isActive) {
            log.warn("产品不存在或已软删除: productId={}", productId);
        }

        return isActive;
    }

    /**
     * 检查固件版本是否活跃（未被软删除）
     *
     * @param versionId 固件版本ID
     * @return true 如果固件版本存在且未被删除
     */
    public boolean isFirmwareVersionActive(Long versionId) {
        if (versionId == null) {
            return false;
        }

        var version = firmwareVersionMapper.selectById(versionId);
        boolean isActive = version != null && version.getDeletedAt() == null;

        if (!isActive) {
            log.warn("固件版本不存在或已软删除: versionId={}", versionId);
        }

        return isActive;
    }

    /**
     * 检查升级策略是否活跃（未被软删除）
     *
     * @param policyId 升级策略ID
     * @return true 如果升级策略存在且未被删除
     */
    public boolean isUpgradePolicyActive(Long policyId) {
        if (policyId == null) {
            return false;
        }

        var policy = upgradePolicyMapper.selectById(policyId);
        boolean isActive = policy != null && policy.getDeletedAt() == null;

        if (!isActive) {
            log.warn("升级策略不存在或已软删除: policyId={}", policyId);
        }

        return isActive;
    }

    /**
     * 校验升级策略的完整性
     * <p>
     * 检查策略引用的产品和目标固件版本是否都是活跃的
     * </p>
     *
     * @param policyId 升级策略ID
     * @return true 如果策略完整且有效
     */
    public boolean validateUpgradePolicyIntegrity(Long policyId) {
        var policy = upgradePolicyMapper.selectById(policyId);

        if (policy == null) {
            log.warn("升级策略不存在: policyId={}", policyId);
            return false;
        }

        if (policy.getDeletedAt() != null) {
            log.warn("升级策略已软删除: policyId={}", policyId);
            return false;
        }

        // 校验产品
        if (!isProductActive(policy.getProductId())) {
            log.warn("升级策略引用的产品无效: policyId={}, productId={}",
                policyId, policy.getProductId());
            return false;
        }

        // 校验目标固件版本
        if (!isFirmwareVersionActive(policy.getTargetVersionId())) {
            log.warn("升级策略引用的目标固件版本无效: policyId={}, targetVersionId={}",
                policyId, policy.getTargetVersionId());
            return false;
        }

        return true;
    }

    /**
     * 清理设备对软删除产品的引用
     * <p>
     * 将所有引用已软删除产品的设备的 product_id 设置为 NULL
     * </p>
     *
     * @return 清理的记录数
     */
    public int cleanupDeviceProductReferences() {
        log.info("开始清理设备对软删除产品的引用");

        // 注意：这里需要调用自定义的 Mapper 方法
        // 暂时返回 0，后续实现
        // return deviceMapper.cleanupSoftDeletedProductReferences();

        log.info("设备引用清理完成（暂未实现）");
        return 0;
    }
}
