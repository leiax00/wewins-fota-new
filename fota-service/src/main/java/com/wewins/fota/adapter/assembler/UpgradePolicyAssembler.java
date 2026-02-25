package com.wewins.fota.adapter.assembler;

import com.wewins.fota.application.policy.dto.UpgradePolicyReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyRespDTO;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import org.springframework.stereotype.Component;

/**
 * 升级策略 DTO 转换器
 */
@Component
public class UpgradePolicyAssembler {

    /**
     * 将 UpgradePolicyReqDTO 转换为 UpgradePolicy 实体
     */
    public UpgradePolicy toUpgradePolicyEntity(UpgradePolicyReqDTO req) {
        if (req == null) {
            return null;
        }
        return UpgradePolicy.builder()
                .productId(req.getProductId())
                .targetVersionId(req.getFirmwareVersionId())
                .name(req.getName())
                .grayRate(req.getGrayRate())
                .priority(req.getPriority())
                .planTime(req.getPlanTime())
                .status(req.getStatus())
                .remark(req.getRemark())
                .build();
    }

    /**
     * 将 UpgradePolicy 实体转换为 UpgradePolicyRespDTO
     */
    public UpgradePolicyRespDTO toUpgradePolicyResp(UpgradePolicy policy) {
        if (policy == null) {
            return null;
        }
        return UpgradePolicyRespDTO.builder()
                .id(policy.getId())
                .productId(policy.getProductId())
                .firmwareVersionId(policy.getTargetVersionId())
                .name(policy.getName())
                .grayRate(policy.getGrayRate())
                .priority(policy.getPriority())
                .planTime(policy.getPlanTime())
                .status(policy.getStatus())
                .remark(policy.getRemark())
                .createdAt(policy.getCreatedAt())
                .createdBy(policy.getCreatedBy())
                .updatedAt(policy.getUpdatedAt())
                .updatedBy(policy.getUpdatedBy())
                .build();
    }
}
