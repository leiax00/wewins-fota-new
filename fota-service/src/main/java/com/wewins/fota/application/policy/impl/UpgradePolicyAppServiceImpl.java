package com.wewins.fota.application.policy.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.policy.UpgradePolicyAppService;
import com.wewins.fota.application.policy.dto.UpgradePolicyPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.enums.PolicyStatus;
import com.wewins.fota.domain.policy.enums.TargetMode;
import com.wewins.fota.domain.policy.enums.TimeWindowType;
import com.wewins.fota.domain.policy.enums.TriggerMode;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 升级策略应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradePolicyAppServiceImpl implements UpgradePolicyAppService {

    // ==================== 常量定义 ====================

    private static final String PACKAGE_STATUS_READY = "READY";

    // 校验上限常量
    private static final int MAX_SOURCE_VERSIONS = 50;
    private static final int MAX_TARGET_DEVICE_IDS = 1000;
    private static final int MAX_TARGET_DEVICE_BATCH_IDS = 100;
    private static final int MAX_TAG_KEYS = 20;
    private static final Duration DAILY_MAX_DURATION = Duration.ofHours(24);
    private static final Duration RANGE_MAX_DURATION = Duration.ofDays(365);

    // 标签 key 格式验证（字母开头，允许字母数字下划线，最长64字符）
    private static final Pattern TAG_KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$");

    // ==================== 依赖注入 ====================

    private final UpgradePolicyRepository upgradePolicyRepository;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

    // ==================== 查询方法 ====================

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

    // ==================== 修改方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UpgradePolicy createPolicy(UpgradePolicy policy) {
        if (policy == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略信息不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("创建升级策略: productId={}, firmwareVersionId={}, name={}, grayRate={}, priority={}, triggerMode={}, targetMode={}",
                    policy.getProductId(), policy.getTargetVersionId(), policy.getName(),
                    policy.getGrayRate(), policy.getPriority(), policy.getTriggerMode(), policy.getTargetMode());
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

    // ==================== 校验方法 ====================

    /**
     * 规范化并校验策略数据
     *
     * @param policy 策略实体
     * @param creating 是否为创建操作
     */
    private void normalizeAndValidate(UpgradePolicy policy, boolean creating) {
        // 基础字段校验
        validateBasicFields(policy);

        // 优先级校验
        validatePriority(policy);

        // 状态校验
        validateAndNormalizeStatus(policy);

        // 触发模式校验
        normalizeAndValidateTriggerMode(policy);

        // 源版本校验
        normalizeAndValidateSourceVersions(policy);

        // 目标范围校验
        normalizeAndValidateTargetScope(policy);

        // 时间窗口校验
        normalizeAndValidateTimeWindow(policy);

        // 产品和固件版本关联校验
        validateProductAndFirmwareRelation(policy);

        // 策略名称唯一性校验
        validatePolicyNameUniqueness(policy, creating);
    }

    /**
     * 校验基础字段
     */
    private void validateBasicFields(UpgradePolicy policy) {
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

        if (policy.getGrayRate() < 0 || policy.getGrayRate() > 100) {
            throw new BizException(ErrorCode.POLICY_GRAY_RATE_INVALID);
        }
    }

    /**
     * 校验优先级
     */
    private void validatePriority(UpgradePolicy policy) {
        policy.setPriority(policy.getPriority() == null ? 0 : policy.getPriority());
        if (policy.getPriority() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "priority 不能小于 0");
        }
    }

    /**
     * 校验并规范化状态
     */
    private void validateAndNormalizeStatus(UpgradePolicy policy) {
        PolicyStatus status = PolicyStatus.of(policy.getStatus());
        policy.setStatus(status.getCode());
    }

    /**
     * 校验并规范化触发模式
     */
    private void normalizeAndValidateTriggerMode(UpgradePolicy policy) {
        TriggerMode triggerMode = TriggerMode.of(policy.getTriggerMode());
        policy.setTriggerMode(triggerMode.getCode());
    }

    /**
     * 校验并规范化源版本列表
     * <p>
     * 强制要求至少包含一个版本号
     * </p>
     */
    private void normalizeAndValidateSourceVersions(UpgradePolicy policy) {
        JsonNode sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isNull() || !sourceVersions.isArray()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 必须为非空数组");
        }

        ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();

        for (JsonNode item : sourceVersions) {
            if (item == null || !item.isTextual()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 仅支持字符串版本号");
            }
            String version = item.asText().trim();
            if (version.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 不允许包含空字符串");
            }
            deduplicated.add(version);
        }

        if (deduplicated.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 至少包含一个版本");
        }
        if (deduplicated.size() > MAX_SOURCE_VERSIONS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 数量不能超过 " + MAX_SOURCE_VERSIONS);
        }

        deduplicated.forEach(normalized::add);
        policy.setSourceVersions(normalized);
    }

    /**
     * 校验并规范化目标范围
     * <p>
     * 目标模式互斥：只能有一个目标列表非空
     * </p>
     */
    private void normalizeAndValidateTargetScope(UpgradePolicy policy) {
        TargetMode targetMode = TargetMode.of(policy.getTargetMode());
        policy.setTargetMode(targetMode.getCode());

        ArrayNode deviceIds = normalizePositiveStringArray(policy.getTargetDeviceIds(), MAX_TARGET_DEVICE_IDS, "targetDeviceIds");
        ArrayNode batchIds = normalizePositiveStringArray(policy.getTargetDeviceBatchIds(), MAX_TARGET_DEVICE_BATCH_IDS, "targetDeviceBatchIds");
        ObjectNode tags = normalizeTagObject(policy.getTargetDeviceTags());

        switch (targetMode) {
            case ALL -> {
                if (deviceIds != null || batchIds != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=ALL 时不能传目标设备筛选字段");
                }
                policy.setTargetDeviceIds(null);
                policy.setTargetDeviceBatchIds(null);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_IDS -> {
                if (deviceIds == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_IDS 时 targetDeviceIds 必填");
                }
                if (batchIds != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_IDS 时仅允许 targetDeviceIds");
                }
                policy.setTargetDeviceIds(deviceIds);
                policy.setTargetDeviceBatchIds(null);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_BATCHES -> {
                if (batchIds == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_BATCHES 时 targetDeviceBatchIds 必填");
                }
                if (deviceIds != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_BATCHES 时仅允许 targetDeviceBatchIds");
                }
                policy.setTargetDeviceIds(null);
                policy.setTargetDeviceBatchIds(batchIds);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_TAGS -> {
                if (tags == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_TAGS 时 targetDeviceTags 必填");
                }
                if (deviceIds != null || batchIds != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_TAGS 时仅允许 targetDeviceTags");
                }
                policy.setTargetDeviceIds(null);
                policy.setTargetDeviceBatchIds(null);
                policy.setTargetDeviceTags(tags);
            }
        }
    }

    /**
     * 校验并规范化时间窗口
     * <p>
     * 时间区间语义：左闭右开 [startAt, endAt)
     * </p>
     */
    private void normalizeAndValidateTimeWindow(UpgradePolicy policy) {
        JsonNode timeWindow = policy.getTimeWindow();
        if (timeWindow == null || timeWindow.isNull() || !timeWindow.isObject()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow 必须为对象");
        }

        TimeWindowType type = TimeWindowType.of(readRequiredText(timeWindow, "type"));
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow.type 仅支持 RANGE 或 DAILY");
        }

        OffsetDateTime startAt = parseUtcOffsetDateTime(readRequiredText(timeWindow, "startAt"));
        OffsetDateTime endAt = parseUtcOffsetDateTime(readRequiredText(timeWindow, "endAt"));

        // 左闭右开区间：startAt 必须 < endAt
        if (!startAt.isBefore(endAt)) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "timeWindow.startAt 必须早于 timeWindow.endAt（左闭右开区间 [startAt, endAt)）");
        }

        // DAILY 模式：验证时间差不超过 24 小时（支持跨日）
        if (type.isDaily()) {
            Duration duration = Duration.between(startAt, endAt);
            if (duration.compareTo(DAILY_MAX_DURATION) > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "DAILY 模式时间窗口不能超过 24 小时");
            }
        }

        // RANGE 模式：设置合理上限（365 天）
        if (type == TimeWindowType.RANGE) {
            Duration duration = Duration.between(startAt, endAt);
            if (duration.compareTo(RANGE_MAX_DURATION) > 0) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "RANGE 模式时间窗口不能超过 365 天");
            }
        }

        // 规范化存储（转为 UTC Instant 字符串）
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        normalized.put("type", type.getCode());
        normalized.put("startAt", startAt.toInstant().toString());
        normalized.put("endAt", endAt.toInstant().toString());
        policy.setTimeWindow(normalized);
    }

    /**
     * 校验产品和固件版本的关联关系
     */
    private void validateProductAndFirmwareRelation(UpgradePolicy policy) {
        productRepository.findById(policy.getProductId())
                .orElseThrow(() -> new BizException(ErrorCode.PRODUCT_NOT_FOUND));

        FirmwareVersion firmwareVersion = firmwareVersionRepository.findById(policy.getTargetVersionId())
                .orElseThrow(() -> new BizException(ErrorCode.FIRMWARE_VERSION_NOT_FOUND));
        if (!policy.getProductId().equals(firmwareVersion.getProductId())) {
            throw new BizException(ErrorCode.POLICY_FIRMWARE_PRODUCT_MISMATCH);
        }

        // 校验目标固件版本是否包含固件包（packageStatus=READY）
        String packageStatus = firmwareVersion.getPackageStatus();
        if (packageStatus == null || !PACKAGE_STATUS_READY.equalsIgnoreCase(packageStatus)) {
            throw new BizException(ErrorCode.POLICY_TARGET_FIRMWARE_NOT_READY);
        }
    }

    /**
     * 校验策略名称唯一性
     */
    private void validatePolicyNameUniqueness(UpgradePolicy policy, boolean creating) {
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

    // ==================== 辅助方法 ====================

    /**
     * 规范化正整数字符串数组
     *
     * @param node JsonNode 数组
     * @param maxSize 最大数量
     * @param fieldName 字段名称（用于错误消息）
     * @return 规范化后的 ArrayNode，如果输入为空则返回 null
     */
    private ArrayNode normalizePositiveStringArray(JsonNode node, int maxSize, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 必须为数组");
        }
        if (node.isEmpty()) {
            return null;
        }

        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value;
            if (item.isTextual()) {
                value = item.asText().trim();
            } else if (item.isIntegralNumber()) {
                value = String.valueOf(item.asLong());
            } else {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 仅支持字符串或数字");
            }

            if (value.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 不允许包含空值");
            }

            // 验证是否为有效数字（正整数）
            try {
                long num = Long.parseLong(value);
                if (num <= 0) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 仅支持正整数");
                }
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 仅支持正整数");
            }

            deduplicated.add(value);
        }

        if (deduplicated.size() > maxSize) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), fieldName + " 数量不能超过 " + maxSize);
        }

        ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
        deduplicated.forEach(normalized::add);
        return normalized;
    }

    /**
     * 规范化标签对象（深度验证）
     * <p>
     * 验证标签 key 的格式、value 的类型、标签数量限制
     * </p>
     *
     * @param node JsonNode 对象
     * @return 规范化后的 ObjectNode，如果输入为空则返回 null
     */
    private ObjectNode normalizeTagObject(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isObject() || node.isEmpty()) {
            return null;
        }

        // 验证标签数量
        if (node.size() > MAX_TAG_KEYS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "targetDeviceTags 标签数量不能超过 " + MAX_TAG_KEYS);
        }

        // 深度验证并复制
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        var fields = node.fields();

        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();

            // 验证 key 格式
            if (!TAG_KEY_PATTERN.matcher(key).matches()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "targetDeviceTags key 格式非法: " + key + "（必须以字母或下划线开头，仅允许字母数字下划线，最长64字符）");
            }

            // 验证 value 类型（仅支持简单类型）
            JsonNode value = entry.getValue();
            if (!value.isTextual() && !value.isIntegralNumber() && !value.isBoolean()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "targetDeviceTags value 仅支持字符串/数字/布尔类型: key=" + key);
            }

            normalized.set(key, value);
        }

        return normalized;
    }

    /**
     * 从 JsonNode 中读取必填文本字段
     *
     * @param parent 父节点
     * @param fieldName 字段名称
     * @return 字段值（已 trim）
     */
    private String readRequiredText(JsonNode parent, String fieldName) {
        JsonNode valueNode = parent.get(fieldName);
        if (valueNode == null || !valueNode.isTextual() || valueNode.asText().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow." + fieldName + " 不能为空");
        }
        return valueNode.asText().trim();
    }

    /**
     * 解析 UTC OffsetDateTime
     *
     * @param dateTimeStr ISO8601 格式的时间字符串
     * @return OffsetDateTime
     */
    private OffsetDateTime parseUtcOffsetDateTime(String dateTimeStr) {
        try {
            OffsetDateTime parsed = OffsetDateTime.parse(dateTimeStr);
            if (!ZoneOffset.UTC.equals(parsed.getOffset())) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "时间必须是 UTC（例如 2026-02-26T00:00:00Z）");
            }
            return parsed;
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "时间格式必须为 ISO8601 UTC: " + dateTimeStr);
        }
    }
}
