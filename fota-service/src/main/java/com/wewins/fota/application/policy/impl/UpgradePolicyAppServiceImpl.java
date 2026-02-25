package com.wewins.fota.application.policy.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.policy.UpgradePolicyAppService;
import com.wewins.fota.application.policy.dto.UpgradePolicyPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 升级策略应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradePolicyAppServiceImpl implements UpgradePolicyAppService {

    private static final Set<String> ALLOWED_STATUS = Set.of("ACTIVE", "PAUSED", "EXPIRED");

    private final UpgradePolicyRepository upgradePolicyRepository;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

    @Override
    public Page<UpgradePolicy> pagePolicies(UpgradePolicyPageReqDTO reqDTO) {
        if (reqDTO == null) {
            reqDTO = new UpgradePolicyPageReqDTO();
        }
        reqDTO.validate();

        Page<UpgradePolicy> page = new Page<>(reqDTO.getPage(), reqDTO.getSize());

        if (log.isDebugEnabled()) {
            log.debug("分页查询升级策略: productId={}, name={}, status={}, page={}, size={}",
                    reqDTO.getProductId(), reqDTO.getName(), reqDTO.getStatus(), reqDTO.getPage(), reqDTO.getSize());
        }

        return upgradePolicyRepository.pagePolicies(page, reqDTO.getProductId(), reqDTO.getName(), reqDTO.getStatus());
    }

    @Override
    public UpgradePolicy getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return upgradePolicyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.POLICY_NOT_FOUND));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpgradePolicy createPolicy(UpgradePolicy policy) {
        if (policy == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略信息不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("创建升级策略: productId={}, firmwareVersionId={}, name={}, grayRate={}, priority={}, status={}",
                    policy.getProductId(), policy.getTargetVersionId(), policy.getName(),
                    policy.getGrayRate(), policy.getPriority(), policy.getStatus());
        }

        normalizeAndValidate(policy, true);
        upgradePolicyRepository.create(policy);
        log.info("升级策略创建成功: policyId={}, name={}", policy.getId(), policy.getName());
        return policy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpgradePolicy updatePolicy(UpgradePolicy policy) {
        if (policy == null || policy.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新升级策略: policyId={}", policy.getId());
        }

        getById(policy.getId());
        normalizeAndValidate(policy, false);
        upgradePolicyRepository.updateById(policy);
        log.info("升级策略更新成功: policyId={}", policy.getId());
        return policy;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deletePolicy(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除升级策略: policyId={}", id);
        }

        getById(id);
        boolean result = upgradePolicyRepository.deleteById(id);
        log.info("升级策略删除成功: policyId={}, result={}", id, result);
        return result;
    }

    private void normalizeAndValidate(UpgradePolicy policy, boolean creating) {
        if (policy.getProductId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "产品 ID 不能为空");
        }
        if (policy.getTargetVersionId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "目标固件版本 ID 不能为空");
        }
        if (policy.getName() == null || policy.getName().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "策略名称不能为空");
        }

        policy.setName(policy.getName().trim());
        policy.setGrayRate(policy.getGrayRate() == null ? 0 : policy.getGrayRate());
        policy.setPriority(policy.getPriority() == null ? 0 : policy.getPriority());

        if (policy.getGrayRate() < 0 || policy.getGrayRate() > 100) {
            throw new BizException(ErrorCode.POLICY_GRAY_RATE_INVALID);
        }

        String normalizedStatus = policy.getStatus();
        if (normalizedStatus == null || normalizedStatus.isBlank()) {
            normalizedStatus = "ACTIVE";
        } else {
            normalizedStatus = normalizedStatus.trim().toUpperCase();
        }
        if (!ALLOWED_STATUS.contains(normalizedStatus)) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID);
        }
        policy.setStatus(normalizedStatus);

        productRepository.findById(policy.getProductId())
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));

        FirmwareVersion firmwareVersion = firmwareVersionRepository.findById(policy.getTargetVersionId())
                .orElseThrow(() -> new BizException(ErrorCode.FIRMWARE_VERSION_NOT_FOUND));
        if (!policy.getProductId().equals(firmwareVersion.getProductId())) {
            throw new BizException(ErrorCode.POLICY_FIRMWARE_PRODUCT_MISMATCH);
        }

        Long excludeId = creating ? null : policy.getId();
        long duplicateCount = upgradePolicyRepository.countByProductIdAndNameExcludingId(
                policy.getProductId(),
                policy.getName(),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new BizException(ErrorCode.POLICY_NAME_EXISTS);
        }
    }
}
