package com.wewins.fota.adapter.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.policy.dto.TimeWindowDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyRespDTO;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.module.system.service.user.UserCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private final ObjectMapper objectMapper;
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
                .timeWindow(toJsonNode(req.getTimeWindow()))
                .sourceVersions(toJsonNode(req.getSourceVersions()))
                .targetMode(req.getTargetMode())
                .targetImeis(toJsonNode(req.getTargetImeis()))
                .targetDeviceBatchIds(toJsonNode(req.getTargetDeviceBatchIds()))
                .targetDeviceTags(toJsonNode(req.getTargetDeviceTags()))
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
                .sourceVersions(toList(policy.getSourceVersions(), new TypeReference<List<Long>>() {}))
                .targetMode(policy.getTargetMode())
                .targetImeis(toList(policy.getTargetImeis(), new TypeReference<List<String>>() {}))
                .targetDeviceBatchIds(toList(policy.getTargetDeviceBatchIds(), new TypeReference<List<String>>() {}))
                .targetDeviceTags(toMap(policy.getTargetDeviceTags()))
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

    /**
     * 将对象转换为 JsonNode
     * <p>
     * 对 TimeWindowDTO 特殊处理：直接将 LocalDateTime 转为 ISO 字符串
     * 避免 Jackson 的自定义序列化器把 UTC 时间再转回客户端时区
     * </p>
     *
     * @param value 源对象
     * @return JsonNode，如果源对象为 null 则返回 null
     */
    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        // 特殊处理 TimeWindowDTO，绕过 Jackson 的 LocalDateTime 序列化器
        if (value instanceof TimeWindowDTO) {
            return timeWindowToJsonNode((TimeWindowDTO) value);
        }
        return objectMapper.valueToTree(value);
    }

    /**
     * 将 TimeWindowDTO 转换为 JsonNode
     * <p>
     * LocalDateTime 已约定为 UTC，直接转为 ISO 字符串存储，不做时区转换
     * </p>
     *
     * @param timeWindow 时间窗口 DTO
     * @return JsonNode
     */
    private JsonNode timeWindowToJsonNode(TimeWindowDTO timeWindow) {
        if (timeWindow == null) {
            return null;
        }

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", timeWindow.getType());

        LocalDateTime startAt = timeWindow.getStartAt();
        LocalDateTime endAt = timeWindow.getEndAt();

        if (startAt != null) {
            node.put("startAt", startAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            node.putNull("startAt");
        }

        if (endAt != null) {
            node.put("endAt", endAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            node.putNull("endAt");
        }

        return node;
    }

    /**
     * 将 JsonNode 转换为列表
     *
     * @param node JsonNode
     * @param typeReference 类型引用
     * @param <T> 列表元素类型
     * @return 列表，如果 JsonNode 为 null 或空则返回 null
     */
    private <T> List<T> toList(JsonNode node, TypeReference<List<T>> typeReference) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, typeReference);
    }

    /**
     * 将 JsonNode 转换为 Map
     *
     * @param node JsonNode
     * @return Map，如果 JsonNode 为 null 或空则返回 null
     */
    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 将 JsonNode 转换为 TimeWindowDTO
     * <p>
     * 直接读取时间字符串并解析为 LocalDateTime（约定为 UTC）
     * 避免 Jackson 的自定义反序列化器把 UTC 当作客户端时区再转换
     * </p>
     *
     * @param node JsonNode
     * @return TimeWindowDTO，如果 JsonNode 为 null 或空则返回 null
     */
    private TimeWindowDTO toTimeWindow(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        // 直接从 JsonNode 读取字段，绕过 Jackson 的反序列化器
        TimeWindowDTO dto = new TimeWindowDTO();
        dto.setType(node.get("type").asText());

        JsonNode startAtNode = node.get("startAt");
        if (startAtNode != null && !startAtNode.isNull()) {
            String startAtStr = startAtNode.asText();
            dto.setStartAt(LocalDateTime.parse(startAtStr));
        } else {
            dto.setStartAt(null);
        }

        JsonNode endAtNode = node.get("endAt");
        if (endAtNode != null && !endAtNode.isNull()) {
            String endAtStr = endAtNode.asText();
            dto.setEndAt(LocalDateTime.parse(endAtStr));
        } else {
            dto.setEndAt(null);
        }

        return dto;
    }
}
