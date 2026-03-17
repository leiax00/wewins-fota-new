package com.wewins.fota.adapter.assembler;

import com.wewins.fota.application.policy.dto.TimeWindowDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyRespDTO;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.module.system.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 升级策略 DTO 转换器
 * <p>
 * 负责在 DTO 和实体之间进行转换，处理 JSONB 字段的序列化/反序列化
 * 并注入扩展字段（产品名称、版本号、用户名等）
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UpgradePolicyAssembler {

    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final UserCacheService userCacheService;

    /**
     * 将 UpgradePolicyReqDTO 转换为 UpgradePolicy 实体
     *
     * @param req 请求 DTO
     * @return 策略实体
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
                .triggerMode(req.getTriggerMode())
                .timeWindow(toPolicyTimeWindow(req.getTimeWindow()))
                .sourceVersions(toLongSet(req.getSourceVersions()))
                .targetMode(req.getTargetMode())
                .targetImeis(toStringSet(req.getTargetImeis()))
                .targetDeviceBatchIds(toLongSetFromString(req.getTargetDeviceBatchIds()))
                .targetDeviceTags(req.getTargetDeviceTags())
                .status(req.getStatus())
                .remark(req.getRemark())
                .build();
    }

    /**
     * 将 UpgradePolicy 实体转换为 UpgradePolicyRespDTO（带扩展字段）
     *
     * @param policy 策略实体
     * @return 响应 DTO
     */
    public UpgradePolicyRespDTO toUpgradePolicyResp(UpgradePolicy policy) {
        if (policy == null) {
            return null;
        }

        UpgradePolicyRespDTO dto = toUpgradePolicyRespWithoutEnrichment(policy);
        enrichWithExtendedFields(dto);
        return dto;
    }

    /**
     * 批量将 UpgradePolicy 实体列表转换为 UpgradePolicyRespDTO 列表（带扩展字段）
     * <p>
     * 使用批量查询避免 N+1 问题
     * </p>
     *
     * @param policies 策略实体列表
     * @return 响应 DTO 列表
     */
    public List<UpgradePolicyRespDTO> toUpgradePolicyRespList(List<UpgradePolicy> policies) {
        if (policies == null || policies.isEmpty()) {
            return List.of();
        }

        // 1. 基础转换（不查询扩展字段）
        List<UpgradePolicyRespDTO> dtoList = policies.stream()
                .map(this::toUpgradePolicyRespWithoutEnrichment)
                .toList();

        // 2. 批量注入扩展字段
        enrichBatchWithExtendedFields(dtoList);

        return dtoList;
    }

    /**
     * 将 UpgradePolicy 实体转换为 UpgradePolicyRespDTO（不含扩展字段）
     *
     * @param policy 策略实体
     * @return 响应 DTO
     */
    private UpgradePolicyRespDTO toUpgradePolicyRespWithoutEnrichment(UpgradePolicy policy) {
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
                .triggerMode(policy.getTriggerMode())
                .timeWindow(toTimeWindow(policy.getTimeWindow()))
                .sourceVersions(toList(policy.getSourceVersions()))
                .targetMode(policy.getTargetMode())
                .targetImeis(toList(policy.getTargetImeis()))
                .targetDeviceBatchIds(toStringList(policy.getTargetDeviceBatchIds()))
                .targetDeviceTags(policy.getTargetDeviceTags())
                .status(policy.getStatus())
                .remark(policy.getRemark())
                .createdAt(policy.getCreatedAt())
                .createdBy(policy.getCreatedBy())
                .updatedAt(policy.getUpdatedAt())
                .updatedBy(policy.getUpdatedBy())
                .build();
    }

    /**
     * 注入扩展字段（产品名称、版本号、用户名）
     *
     * @param dto 响应 DTO
     */
    private void enrichWithExtendedFields(UpgradePolicyRespDTO dto) {
        // 注入产品名称
        if (dto.getProductId() != null) {
            Optional<Product> product = productRepository.findById(dto.getProductId());
            product.ifPresent(value -> dto.setProductName(value.getName()));
        }

        // 注入固件版本号和源版本名称映射
        List<Long> sourceVersions = dto.getSourceVersions() != null ? dto.getSourceVersions() : List.of();
        HashSet<Long> versionIds = new HashSet<>(sourceVersions);
        if (dto.getFirmwareVersionId() != null) {
            versionIds.add(dto.getFirmwareVersionId());
        }
        Map<Long, String> versionNames = firmwareVersionRepository.findVersionNamesByIds(versionIds.stream().toList());
        dto.setSourceVersionNames(versionNames);
        dto.setFirmwareVersion(versionNames.get(dto.getFirmwareVersionId()));

        // 注入创建人和修改人姓名
        Set<Long> userIds = Stream.of(dto.getCreatedBy(), dto.getUpdatedBy())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> userNames = userCacheService.getUserNames(userIds);

        if (dto.getCreatedBy() != null) {
            dto.setCreatedByName(userNames.get(dto.getCreatedBy()));
        }
        if (dto.getUpdatedBy() != null) {
            dto.setUpdatedByName(userNames.get(dto.getUpdatedBy()));
        }
    }

    /**
     * 批量注入扩展字段（产品名称、版本号、用户名）
     * <p>
     * 使用批量查询避免 N+1 问题
     * </p>
     *
     * @param dtoList 响应 DTO 列表
     */
    private void enrichBatchWithExtendedFields(List<UpgradePolicyRespDTO> dtoList) {
        if (dtoList == null || dtoList.isEmpty()) {
            return;
        }

        // 1. 批量查询产品名称
        Set<Long> productIds = dtoList.stream()
                .map(UpgradePolicyRespDTO::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> productNames = findProductNamesByIds(productIds);

        // 2. 批量查询固件版本号
        Set<Long> versionIds = dtoList.stream()
                .flatMap(dto -> {
                    Stream<Long> sourceVersions = dto.getSourceVersions() != null
                            ? dto.getSourceVersions().stream()
                            : Stream.of();
                    return Stream.concat(sourceVersions, Stream.of(dto.getFirmwareVersionId()));
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> versionNames = firmwareVersionRepository.findVersionNamesByIds(versionIds.stream().toList());

        // 3. 批量查询用户名称
        Set<Long> userIds = dtoList.stream()
                .flatMap(dto -> Stream.of(dto.getCreatedBy(), dto.getUpdatedBy()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> userNames = userCacheService.getUserNames(userIds);

        // 4. 统一设置扩展字段
        for (UpgradePolicyRespDTO dto : dtoList) {
            dto.setProductName(productNames.get(dto.getProductId()));
            dto.setSourceVersionNames(versionNames);
            dto.setFirmwareVersion(versionNames.get(dto.getFirmwareVersionId()));
            dto.setCreatedByName(userNames.get(dto.getCreatedBy()));
            dto.setUpdatedByName(userNames.get(dto.getUpdatedBy()));
        }
    }

    /**
     * 批量查询产品名称
     *
     * @param productIds 产品ID集合
     * @return 产品ID到产品名称的映射
     */
    private Map<Long, String> findProductNamesByIds(Set<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        List<Product> products = productRepository.listByIds(productIds.stream().toList());
        return products.stream()
                .collect(Collectors.toMap(
                        Product::getId,
                        Product::getName,
                        (existing, replacement) -> existing // 处理重复key
                ));
    }

    private Set<Long> toLongSet(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new LinkedHashSet<>(values);
    }

    private Set<String> toStringSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> toLongSetFromString(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            result.add(Long.parseLong(value.trim()));
        }
        return result;
    }

    private <T> List<T> toList(Set<T> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new ArrayList<>(values);
    }

    private List<String> toStringList(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(String::valueOf).toList();
    }

    private PolicyTimeWindow toPolicyTimeWindow(TimeWindowDTO dto) {
        if (dto == null) {
            return null;
        }
        return PolicyTimeWindow.builder()
                .type(TimeWindowType.of(dto.getType()))
                .startAt(dto.getStartAt())
                .endAt(dto.getEndAt())
                .build();
    }

    private TimeWindowDTO toTimeWindow(PolicyTimeWindow window) {
        if (window == null) {
            return null;
        }
        TimeWindowDTO dto = new TimeWindowDTO();
        dto.setType(window.getType() == null ? null : window.getType().getCode());
        dto.setStartAt(window.getStartAt());
        dto.setEndAt(window.getEndAt());
        return dto;
    }
}
