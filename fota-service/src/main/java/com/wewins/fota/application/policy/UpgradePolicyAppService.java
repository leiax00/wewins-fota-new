package com.wewins.fota.application.policy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wewins.fota.application.policy.dto.UpgradePolicyPageReqDTO;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.common.exception.ErrorCode;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TargetMode;
import com.wewins.fota.domain.policy.model.enums.TimeWindowType;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import com.wewins.fota.domain.policy.model.vo.PolicyTimeWindow;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.PolicyChangedEvent;
import com.wewins.fota.module.system.security.RbacExpressionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 升级策略应用服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradePolicyAppService {

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

    // IMEI 格式验证：15位数字
    private static final Pattern IMEI_PATTERN = Pattern.compile("^\\d{15}$");

    // ==================== 依赖注入 ====================

    private final UpgradePolicyRepository upgradePolicyRepository;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final RbacExpressionService rbacExpressionService;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 查询方法 ====================

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

    public UpgradePolicy getById(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        return upgradePolicyRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.POLICY_NOT_FOUND));
    }

    // ==================== 修改方法 ====================

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

        // 解析请求的状态
        PolicyStatus requestedStatus = policy.getStatus();
        if (requestedStatus == null) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "策略状态不能为空");
        }

        // 涉及 ACTIVE/PAUSED 的状态需要 release 权限
        if ((requestedStatus.isActive() || requestedStatus.isPaused()) &&
            !rbacExpressionService.has("fota:policy:release")) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                String.format("创建 %s 状态的策略需要发布权限", requestedStatus.getDisplayName()));
        }

        normalizeAndValidate(policy, true);
        upgradePolicyRepository.create(policy);
        eventPublisher.publishEvent(new PolicyChangedEvent(this, policy.getProductId(), policy.getId(), ChangeType.CREATED));
        log.info("升级策略创建成功: policyId={}, name={}, status={}", policy.getId(), policy.getName(), policy.getStatus());
        return policy;
    }

    @Transactional(rollbackFor = Exception.class)
    public UpgradePolicy updatePolicy(UpgradePolicy policy) {
        if (policy == null || policy.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新升级策略: policyId={}", policy.getId());
        }

        // 先获取策略检查当前状态和权限
        UpgradePolicy existingPolicy = getById(policy.getId());
        PolicyStatus currentStatus = existingPolicy.getStatus();
        if (currentStatus == null) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "当前策略状态无效");
        }

        // 检查是否有状态变更
        PolicyStatus targetStatus = currentStatus;
        if (policy.getStatus() != null) {
            targetStatus = policy.getStatus();
        }

        // 如果状态有变更，需要检查切换权限并使用并发保护
        if (currentStatus != targetStatus) {
            boolean hasReleasePermission = rbacExpressionService.has("fota:policy:release");

            // 涉及 ACTIVE/PAUSED 的状态切换需要 release 权限
            if (requiresReleaseForTransition(currentStatus, targetStatus) && !hasReleasePermission) {
                throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                    String.format("从 %s 切换到 %s 需要发布权限",
                        currentStatus.getDisplayName(), targetStatus.getDisplayName()));
            }

            log.info("策略状态通过更新接口变更: policyId={}, from={}, to={}",
                policy.getId(), currentStatus, targetStatus);

            // 使用带状态校验的更新方法，防止并发冲突
            normalizeAndValidate(policy, false);
            UpgradePolicy updated = upgradePolicyRepository.updateWithStatusCheck(
                policy.getId(), currentStatus, policy);

            if (updated == null) {
                throw new BizException(
                    ErrorCode.POLICY_INVALID_STATUS_TRANSITION.getCode(),
                    String.format("状态更新失败: 策略状态已发生变化，请刷新后重试（当前状态: %s）",
                        currentStatus.getDisplayName())
                );
            }

            log.info("升级策略更新成功: policyId={}, statusChanged=true", policy.getId());
            eventPublisher.publishEvent(new PolicyChangedEvent(this, policy.getProductId(), policy.getId(), ChangeType.UPDATED));
            return updated;
        }

        // 无状态变更时，检查修改权限
        if (requiresReleaseForUpdate(currentStatus) && !rbacExpressionService.has("fota:policy:release")) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                String.format("%s 状态的策略不能修改，需要发布权限", currentStatus.getDisplayName()));
        }

        // 保持原状态不变（防止被篡改）
        policy.setStatus(currentStatus);

        normalizeAndValidate(policy, false);
        upgradePolicyRepository.updateById(policy);
        eventPublisher.publishEvent(new PolicyChangedEvent(this, policy.getProductId(), policy.getId(), ChangeType.UPDATED));
        log.info("升级策略更新成功: policyId={}, statusChanged=false", policy.getId());
        return policy;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deletePolicy(Long id) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略 ID 不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("删除升级策略: policyId={}", id);
        }

        UpgradePolicy policy = getById(id);
        PolicyStatus currentStatus = policy.getStatus();
        if (currentStatus == null) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "策略状态无效");
        }

        // 检查用户权限
        boolean hasReleasePermission = rbacExpressionService.has("fota:policy:release");

        // 统一删除权限检查
        if (!canDelete(currentStatus, hasReleasePermission)) {
            if (currentStatus.isActive()) {
                throw new BizException(ErrorCode.POLICY_IN_USE.getCode(),
                    "生产中的策略不能删除，请先切换到其他状态后再删除");
            }
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                String.format("%s 状态的策略删除需要发布权限", currentStatus.getDisplayName()));
        }

        boolean result = upgradePolicyRepository.deleteById(id);
        eventPublisher.publishEvent(new PolicyChangedEvent(this, policy.getProductId(), id, ChangeType.DELETED));
        log.info("升级策略删除成功: policyId={}, status={}, result={}", id, currentStatus.getCode(), result);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public UpgradePolicy updateStatus(Long id, String newStatus) {
        if (id == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "升级策略 ID 不能为空");
        }

        if (newStatus == null || newStatus.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "新状态不能为空");
        }

        if (log.isDebugEnabled()) {
            log.debug("更新策略状态: policyId={}, newStatus={}", id, newStatus);
        }

        // 解析目标状态
        final PolicyStatus targetStatus;
        try {
            targetStatus = PolicyStatus.of(newStatus);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "目标状态无效: " + e.getMessage());
        }

        // 获取策略
        UpgradePolicy policy = getById(id);

        // 获取当前状态
        final PolicyStatus currentStatus = policy.getStatus();
        if (currentStatus == null) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "当前策略状态无效");
        }

        // ==================== 权限检查 ====================
        // 检查是否涉及受保护的状态（ACTIVE 和 PAUSED 需要特殊权限）
        boolean hasReleasePermission = rbacExpressionService.has("fota:policy:release");

        // 涉及 ACTIVE/PAUSED 状态的操作需要 release 权限
        if (requiresReleaseForTransition(currentStatus, targetStatus) && !hasReleasePermission) {
            throw new BizException(ErrorCode.FORBIDDEN.getCode(),
                "当前状态切换需要发布权限");
        }

        // ==================== 执行状态切换 ====================
        // 使用带状态校验的更新方法，防止并发冲突
        policy.setStatus(targetStatus);
        UpgradePolicy updated = upgradePolicyRepository.updateWithStatusCheck(id, currentStatus, policy);

        if (updated == null) {
            // 状态已被其他事务修改，抛出异常
            throw new BizException(
                    ErrorCode.POLICY_INVALID_STATUS_TRANSITION.getCode(),
                    String.format("状态更新失败: 策略状态已发生变化，请刷新后重试（当前状态: %s）",
                            currentStatus.getDisplayName())
            );
        }

        log.info("策略状态更新成功: policyId={}, from={}, to={}",
                id, currentStatus.getCode(), targetStatus.getCode());
        eventPublisher.publishEvent(new PolicyChangedEvent(this, policy.getProductId(), id, ChangeType.STATUS_CHANGED));

        return updated;
    }

    // ==================== 校验方法 ====================

    /**
     * 状态切换是否需要 release 权限
     * <p>
     * 规则：只要当前状态或目标状态涉及 ACTIVE/PAUSED，就需要 release 权限
     * </p>
     *
     * @param from 当前状态
     * @param to 目标状态
     * @return true 如果需要 release 权限
     */
    private boolean requiresReleaseForTransition(PolicyStatus from, PolicyStatus to) {
        return from.isActive() || from.isPaused() || to.isActive() || to.isPaused();
    }

    /**
     * 当前状态修改是否需要 release 权限
     * <p>
     * 规则：ACTIVE 或 PAUSED 状态的策略修改需要 release 权限
     * </p>
     *
     * @param current 当前状态
     * @return true 如果需要 release 权限
     */
    private boolean requiresReleaseForUpdate(PolicyStatus current) {
        return current.isActive() || current.isPaused();
    }

    /**
     * 删除权限判定
     * <p>
     * 规则：
     * <ul>
     *   <li>ACTIVE：永不允许删除（必须先切换到其他状态）</li>
     *   <li>PAUSED：需要 release 权限</li>
     *   <li>其他状态：允许删除（前置已有 delete 基础权限检查）</li>
     * </ul>
     * </p>
     *
     * @param current 当前状态
     * @param hasRelease 是否有 release 权限
     * @return true 如果允许删除
     */
    private boolean canDelete(PolicyStatus current, boolean hasRelease) {
        if (current.isActive()) {
            return false;
        }
        if (current.isPaused()) {
            return hasRelease;
        }
        return true;
    }

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
        if (policy.getStatus() == null) {
            throw new BizException(ErrorCode.POLICY_STATUS_INVALID.getCode(), "策略状态不能为空");
        }
    }

    /**
     * 校验并规范化触发模式
     */
    private void normalizeAndValidateTriggerMode(UpgradePolicy policy) {
        if (policy.getTriggerMode() == null) {
            policy.setTriggerMode(TriggerMode.BOTH);
        }
    }

    /**
     * 校验并规范化源版本列表
     * <p>
     * 强制要求至少包含一个版本 ID（数字）
     * </p>
     */
    private void normalizeAndValidateSourceVersions(UpgradePolicy policy) {
        Set<Long> sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 必须为非空数组");
        }

        LinkedHashSet<Long> deduplicated = new LinkedHashSet<>();

        for (Long versionId : sourceVersions) {
            if (versionId == null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 不允许包含空值");
            }
            if (versionId <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 版本 ID 必须为正整数");
            }
            deduplicated.add(versionId);
        }

        if (deduplicated.size() > MAX_SOURCE_VERSIONS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "sourceVersions 数量不能超过 " + MAX_SOURCE_VERSIONS);
        }

        policy.setSourceVersions(deduplicated);
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

        Set<String> imeis = normalizeImeiSet(policy.getTargetImeis());
        Set<Long> batchIds = normalizePositiveLongSet(policy.getTargetDeviceBatchIds());
        Map<String, Object> tags = normalizeTagMap(policy.getTargetDeviceTags());

        switch (targetMode) {
            case ALL -> {
                if (imeis != null || batchIds != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=ALL 时不能传目标设备筛选字段");
                }
                policy.setTargetImeis(null);
                policy.setTargetDeviceBatchIds(null);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_IDS -> {
                if (imeis == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_IDS 时 targetImeis 必填");
                }
                if (batchIds != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_IDS 时仅允许 targetImeis");
                }
                policy.setTargetImeis(imeis);
                policy.setTargetDeviceBatchIds(null);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_BATCHES -> {
                if (batchIds == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_BATCHES 时 targetDeviceBatchIds 必填");
                }
                if (imeis != null || tags != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_BATCHES 时仅允许 targetDeviceBatchIds");
                }
                policy.setTargetImeis(null);
                policy.setTargetDeviceBatchIds(batchIds);
                policy.setTargetDeviceTags(null);
            }
            case DEVICE_TAGS -> {
                if (tags == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_TAGS 时 targetDeviceTags 必填");
                }
                if (imeis != null || batchIds != null) {
                    throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetMode=DEVICE_TAGS 时仅允许 targetDeviceTags");
                }
                policy.setTargetImeis(null);
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
     * <p>
     * 支持三种类型：UNLIMITED（不限制）、RANGE（固定范围）、DAILY（每日周期）
     * </p>
     */
    private void normalizeAndValidateTimeWindow(UpgradePolicy policy) {
        PolicyTimeWindow timeWindow = policy.getTimeWindow();
        if (timeWindow == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow 必须为对象");
        }

        TimeWindowType type = timeWindow.getType();
        if (type == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow.type 仅支持 UNLIMITED、RANGE 或 DAILY");
        }

        // UNLIMITED 模式：时间字段必须为 null 或不存在
        if (type == TimeWindowType.UNLIMITED) {
            if (timeWindow.getStartAt() != null || timeWindow.getEndAt() != null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "timeWindow 在 UNLIMITED 模式下 startAt 和 endAt 必须为 null");
            }
            policy.setTimeWindow(PolicyTimeWindow.builder().type(type).startAt(null).endAt(null).build());
            return;
        }

        // RANGE 和 DAILY 模式：时间字段不能为空
        LocalDateTime startAt = timeWindow.getStartAt();
        LocalDateTime endAt = timeWindow.getEndAt();
        if (startAt == null || endAt == null) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "timeWindow.startAt 和 timeWindow.endAt 不能为空");
        }

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

        policy.setTimeWindow(PolicyTimeWindow.builder().type(type).startAt(startAt).endAt(endAt).build());
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
        if (!PACKAGE_STATUS_READY.equalsIgnoreCase(packageStatus)) {
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

    private Set<Long> normalizePositiveLongSet(Set<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashSet<Long> deduplicated = new LinkedHashSet<>();
        for (Long value : values) {
            if (value == null) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetDeviceBatchIds" + " 不允许包含空值");
            }
            if (value <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetDeviceBatchIds" + " 仅支持正整数");
            }
            deduplicated.add(value);
        }
        if (deduplicated.size() > UpgradePolicyAppService.MAX_TARGET_DEVICE_BATCH_IDS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetDeviceBatchIds" + " 数量不能超过 " + UpgradePolicyAppService.MAX_TARGET_DEVICE_BATCH_IDS);
        }
        return deduplicated;
    }

    private Set<String> normalizeImeiSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        for (String raw : values) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetImeis" + " 不允许包含空值");
            }

            // 验证IMEI格式：15位数字
            if (!IMEI_PATTERN.matcher(value).matches()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "targetImeis" + " 必须为15位数字（IMEI格式）: " + value);
            }

            deduplicated.add(value);
        }

        if (deduplicated.size() > UpgradePolicyAppService.MAX_TARGET_DEVICE_IDS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "targetImeis" + " 数量不能超过 " + UpgradePolicyAppService.MAX_TARGET_DEVICE_IDS);
        }
        return deduplicated;
    }

    private Map<String, Object> normalizeTagMap(Map<String, Object> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }

        // 验证标签数量
        if (tags.size() > MAX_TAG_KEYS) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                    "targetDeviceTags 标签数量不能超过 " + MAX_TAG_KEYS);
        }

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : tags.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().trim();

            // 验证 key 格式
            if (!TAG_KEY_PATTERN.matcher(key).matches()) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "targetDeviceTags key 格式非法: " + key + "（必须以字母或下划线开头，仅允许字母数字下划线，最长64字符）");
            }

            // 验证 value 类型（仅支持简单类型）
            Object value = entry.getValue();
            if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(),
                        "targetDeviceTags value 仅支持字符串/数字/布尔类型: key=" + key);
            }
            normalized.put(key, value);
        }

        return normalized;
    }
}
