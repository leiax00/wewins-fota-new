package com.wewins.fota.adapter.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.application.policy.dto.TimeWindowDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyReqDTO;
import com.wewins.fota.application.policy.dto.UpgradePolicyRespDTO;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 升级策略 DTO 转换器
 * <p>
 * 负责在 DTO 和实体之间进行转换，处理 JSONB 字段的序列化/反序列化
 * </p>
 */
@Component
@RequiredArgsConstructor
public class UpgradePolicyAssembler {

    private final ObjectMapper objectMapper;

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
                .targetDeviceIds(toJsonNode(req.getTargetDeviceIds()))
                .targetDeviceBatchIds(toJsonNode(req.getTargetDeviceBatchIds()))
                .targetDeviceTags(toJsonNode(req.getTargetDeviceTags()))
                .status(req.getStatus())
                .remark(req.getRemark())
                .build();
    }

    /**
     * 将 UpgradePolicy 实体转换为 UpgradePolicyRespDTO
     *
     * @param policy 策略实体
     * @return 响应 DTO
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
                .triggerMode(policy.getTriggerMode())
                .timeWindow(toTimeWindow(policy.getTimeWindow()))
                .sourceVersions(toList(policy.getSourceVersions(), new TypeReference<List<String>>() {}))
                .targetMode(policy.getTargetMode())
                .targetDeviceIds(toList(policy.getTargetDeviceIds(), new TypeReference<List<String>>() {}))
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
     * 将对象转换为 JsonNode
     *
     * @param value 源对象
     * @return JsonNode，如果源对象为 null 则返回 null
     */
    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return null;
        }
        return objectMapper.valueToTree(value);
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
     *
     * @param node JsonNode
     * @return TimeWindowDTO，如果 JsonNode 为 null 或空则返回 null
     */
    private TimeWindowDTO toTimeWindow(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return objectMapper.convertValue(node, TimeWindowDTO.class);
    }
}
