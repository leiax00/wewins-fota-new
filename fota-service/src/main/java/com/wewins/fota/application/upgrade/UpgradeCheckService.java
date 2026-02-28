package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.quota.PolicyQuotaService;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.common.exception.BizException;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备检查升级应用服务
 * <p>
 * 跨领域编排服务，负责处理设备检查更新逻辑
 * 协调设备、产品、固件、策略等多个领域
 * </p>
 * <p>
 * 主要职责：
 * </p>
 * <ul>
 *   <li>验证设备有效性</li>
 *   <li>匹配适用的升级策略</li>
 *   <li>检查灰度发布规则</li>
 *   <li>检查时间窗口</li>
 *   <li>检查配额限制</li>
 *   <li>生成交分检查间隔建议</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpgradeCheckService {

    private final DeviceRepository deviceRepository;
    private final DeviceCacheRepository deviceCacheService;
    private final DataIntegrityService dataIntegrityService;
    private final UpgradePolicyRepository upgradePolicyRepository;
    private final DeviceRateLimiter deviceRateLimiter;
    private final DeviceActivityBitmapRepository bitmapRepository;
    private final PolicyMatcher policyMatcher;
    private final ProductRepository productRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FirmwareVersionLookupService firmwareVersionLookupService;
    private final GrayReleaseService grayReleaseService;
    private final UpgradeResponseBuilder upgradeResponseBuilder;
    private final PolicyQuotaService policyQuotaService;
    private final UpgradeRequestValidator requestValidator;

    /**
     * 检查设备是否有可用更新（兼容老接口，默认手动检查）
     *
     * @param imei 设备 IMEI
     * @return 检查结果
     */
    public CheckResult checkUpgrade(String imei) {
        return checkUpgrade(imei, 0);
    }

    /**
     * 检查设备是否有可用更新
     * <p>
     * 核心业务逻辑：
     * 1. 限流检查（防止设备频繁请求）
     * 2. 验证设备存在且激活
     * 3. 标记设备活跃
     * 4. 匹配适用的升级策略
     * 5. 检查灰度发布
     * 6. 检查时间窗口
     * 7. 检查配额限制
     * </p>
     *
     * @param imei 设备 IMEI
     * @param auto 自动检查标识：0=手动检查，1=自动检查，null=默认为手动检查
     * @return 检查结果
     */
    public CheckResult checkUpgrade(String imei, Integer auto) {
        log.debug("开始检查设备更新: imei={}, auto={}", imei, auto);

        // 1. 限流检查（每分钟最多 10 次请求）
        RateLimitDecision rateLimitDecision = deviceRateLimiter.allow(
                "upgrade:" + imei,
                10,  // 每分钟最多 10 次
                Duration.ofMinutes(1)
        );

        if (!rateLimitDecision.isAllowed()) {
            log.warn("设备请求被限流: imei={}, reason={}", imei, rateLimitDecision.getReason());
            return CheckResult.rateLimited(
                    "请求过于频繁",
                    (int) (rateLimitDecision.getResetAtEpochSecond() - System.currentTimeMillis() / 1000)
            );
        }

        // 2. 从缓存或数据库加载设备信息
        // 重要：新系统要求设备必须预先导入，设备不存在时拒绝升级（不创建设备）
        Device device = loadDevice(imei);
        if (device == null) {
            log.warn("设备不存在，拒绝升级: imei={}", imei);
            return CheckResult.notFound("设备未注册，请联系管理员");
        }

        // 3. 标记设备活跃（使用设备 ID 作为 bitmap 偏移量）
        try {
            bitmapRepository.markActive(LocalDate.now(), device.getId());
            log.debug("标记设备活跃: imei={}, deviceId={}", imei, device.getId());
        } catch (Exception e) {
            log.error("标记设备活跃失败: imei={}, deviceId={}", imei, device.getId(), e);
            // 降级处理：记录错误但不中断主流程
        }

        // 4. 验证产品和固件版本
        if (!validateProductAndFirmware(device)) {
            return CheckResult.error("产品或固件版本配置无效");
        }

        // 5. 匹配适用的升级策略
        List<UpgradePolicy> policies = findApplicablePolicies(device);
        if (policies.isEmpty()) {
            log.debug("未找到适用的升级策略: deviceId={}", device.getId());
            return CheckResult.noUpdate();
        }

        // TODO: 实现灰度检查、时间窗口检查、配额检查
        // 当前选择优先级最高的策略
        UpgradePolicy policy = policies.getFirst();

        // 6. 构建响应（使用 UpgradeResponseBuilder）
        return buildCheckResult(device, policy, null, auto == 1);
    }

    /**
     * 检查设备是否有可用更新（完整参数版本）
     * <p>
     * 新 API 接口，支持完整参数列表
     * </p>
     *
     * @param productModel 产品型号（必填）
     * @param imei         设备 IMEI（必填）
     * @param version      当前固件版本号（必填）
     * @param tag          内部版本号/build tag（可选）
     * @param auto         触发模式：0=手动, 1=自动（可选，默认0）
     * @param lang         语言代码（可选）
     * @param dev          测试设备标识：1=测试设备（可选）
     * @return 检查结果
     */
    public CheckResult checkUpgrade(
            String productModel,
            String imei,
            String version,
            String tag,
            Integer auto,
            String lang,
            Integer dev) {

        log.debug("开始检查设备更新: productModel={}, imei={}, version={}, tag={}, auto={}, lang={}, dev={}",
                productModel, imei, version, tag, auto, lang, dev);

        // 1. 参数校验
        requestValidator.validateRequiredParams(productModel, imei, version);
        requestValidator.validateAuto(auto);

        // 2. 限流检查（每分钟最多 10 次请求）
        RateLimitDecision rateLimitDecision = deviceRateLimiter.allow(
                "upgrade:" + imei,
                10,
                Duration.ofMinutes(1)
        );

        if (!rateLimitDecision.isAllowed()) {
            log.warn("设备请求被限流: imei={}, reason={}", imei, rateLimitDecision.getReason());
            return CheckResult.rateLimited(
                    "请求过于频繁",
                    (int) (rateLimitDecision.getResetAtEpochSecond() - System.currentTimeMillis() / 1000)
            );
        }

        // 3. 通过产品型号查找产品
        Product product = productRepository.findByModel(productModel)
                .orElse(null);
        if (product == null) {
            log.warn("产品型号不存在: productModel={}", productModel);
            return CheckResult.error("产品型号不存在");
        }

        // 4. 从缓存或数据库加载设备信息
        // 重要：新系统要求设备必须预先导入，设备不存在时拒绝升级（不创建设备）
        Device device = loadDevice(imei);
        if (device == null) {
            log.warn("设备不存在，拒绝升级: imei={}", imei);
            return CheckResult.notFound("设备未注册，请联系管理员");
        }

        // 5. 标记设备活跃（使用设备 ID 作为 bitmap 偏移量）
        try {
            bitmapRepository.markActive(LocalDate.now(), device.getId());
            log.debug("标记设备活跃: imei={}, deviceId={}", imei, device.getId());
        } catch (Exception e) {
            log.error("标记设备活跃失败: imei={}, deviceId={}", imei, device.getId(), e);
            // 降级处理：记录错误但不中断主流程
        }

        // 6. 查找固件版本 ID（version+tag 组合优先）
        Long versionId = firmwareVersionLookupService.findVersionId(version, tag, product.getId());
        log.debug("查找固件版本 ID: version={}, tag={}, productId={}, versionId={}",
                version, tag, product.getId(), versionId);

        // 7. 匹配适用的升级策略（支持 dev、auto 参数）
        List<UpgradePolicy> policies = findApplicablePolicies(device, versionId, dev, auto);
        if (policies.isEmpty()) {
            log.debug("未找到适用的升级策略: deviceId={}, versionId={}", device.getId(), versionId);
            return CheckResult.noUpdate();
        }

        // 8. 选择优先级最高的策略
        UpgradePolicy policy = policies.get(0);

        // 9. 构建响应（支持 lang 和 auto 参数）
        return upgradeResponseBuilder.buildResponse(device, policy, lang, auto == 1);
    }

    /**
     * 从缓存或数据库加载设备信息
     *
     * @param imei 设备 IMEI
     * @return 设备信息，如果不存在则返回 null
     */
    private Device loadDevice(String imei) {
        // 先从缓存获取
        DeviceCache cached = deviceCacheService.get(imei);
        if (cached != null) {
            log.debug("设备缓存命中: imei={}, deviceId={}", imei, cached.getDeviceId());
            // 缓存命中，直接返回（假设缓存中已有完整信息）
            // 实际场景可能需要查询数据库获取最新状态
        }

        // 缓存未命中，从数据库加载
        Device device = deviceRepository.findByImei(imei).orElse(null);
        if (device != null) {
            // 写入缓存
            // deviceCacheService.put(imei, buildDeviceCache(device));
        }

        return device;
    }

    /**
     * 验证产品和固件版本是否有效
     *
     * @param device 设备信息
     * @return true 如果有效
     */
    private boolean validateProductAndFirmware(Device device) {
        if (device == null) {
            return false;
        }
        return dataIntegrityService.isProductActive(device.getProductId())
                && dataIntegrityService.isFirmwareVersionActive(device.getCurrentVersionId());
    }

    /**
     * 查找适用的升级策略
     * <p>
     * 根据产品ID、当前版本ID、优先级查找匹配的策略
     * </p>
     *
     * @param device 设备信息
     * @return 适用的策略列表（按优先级降序）
     */
    private List<UpgradePolicy> findApplicablePolicies(Device device) {
        if (device == null || device.getProductId() == null) {
            return List.of();
        }

        // 按产品拉取激活策略并按优先级降序
        List<UpgradePolicy> policies = upgradePolicyRepository
                .findActiveByProductIdOrderByPriorityDesc(device.getProductId());

        // 获取设备信息用于匹配
        Long versionId = device.getCurrentVersionId();
        com.fasterxml.jackson.databind.JsonNode deviceTags = device.getTags();
        String imei = device.getImei();

        // 过滤：版本范围匹配 + 标签匹配 + 灰度检查 + 配额检查
        return policies.stream()
                .filter(policy -> matchesSourceVersion(policy, versionId))
                .filter(policy -> matchesDeviceTags(policy, deviceTags))
                .filter(policy -> matchesGrayRelease(policy, imei))
                .filter(policy -> matchesQuota(policy))
                .toList();
    }

    /**
     * 查找适用的升级策略（支持 dev、auto 参数）
     * <p>
     * 根据产品ID、当前版本ID、dev参数、auto参数查找匹配的策略
     * </p>
     *
     * @param device    设备信息
     * @param versionId 当前版本 ID（可能为 null）
     * @param dev       临时测试设备标识（1=测试设备）
     * @param auto      触发模式（0=手动, 1=自动）
     * @return 适用的策略列表（按优先级降序）
     */
    private List<UpgradePolicy> findApplicablePolicies(Device device, Long versionId, Integer dev, Integer auto) {
        if (device == null || device.getProductId() == null) {
            return List.of();
        }

        // 按产品拉取激活策略并按优先级降序
        List<UpgradePolicy> policies = upgradePolicyRepository
                .findActiveByProductIdOrderByPriorityDesc(device.getProductId());

        // 获取设备标签（用于 env 标签匹配）
        com.fasterxml.jackson.databind.JsonNode deviceTags = device.getTags();
        com.fasterxml.jackson.databind.JsonNode augmentedTags = augmentTagsWithDevMode(deviceTags, dev);

        // 过滤策略
        final com.fasterxml.jackson.databind.JsonNode finalTags = augmentedTags;
        final Long finalVersionId = versionId;
        final String imei = device.getImei();

        return policies.stream()
                .filter(policy -> matchesDevMode(policy, dev, finalTags))
                .filter(policy -> matchesTriggerMode(policy, auto))
                .filter(policy -> matchesSourceVersion(policy, finalVersionId))
                .filter(policy -> policyMatcher.matchesDeviceTags(policy.getTargetDeviceTags(), finalTags))
                .filter(policy -> policyMatcher.matchesTimeWindow(policy.getTimeWindow()))
                .filter(policy -> matchesGrayRelease(policy, imei))
                .filter(policy -> matchesQuota(policy))
                .toList();
    }

    /**
     * 使用 dev 参数临时增强设备标签
     * <p>
     * dev=1 表示临时标注为测试设备，类似在标签中添加 env='test'
     * </p>
     *
     * @param deviceTags 原始设备标签
     * @param dev        临时测试设备标识
     * @return 增强后的标签
     */
    private com.fasterxml.jackson.databind.JsonNode augmentTagsWithDevMode(
            com.fasterxml.jackson.databind.JsonNode deviceTags, Integer dev) {
        if (dev == null || dev != 1) {
            return deviceTags;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode augmented;

            if (deviceTags == null || deviceTags.isEmpty() || deviceTags.isNull()) {
                augmented = mapper.createObjectNode();
            } else {
                augmented = deviceTags.deepCopy();
            }

            if (!augmented.has("env")) {
                augmented.put("env", "test");
            }

            return augmented;
        } catch (Exception e) {
            log.warn("增强设备标签失败，使用原始标签", e);
            return deviceTags;
        }
    }

    /**
     * dev 参数匹配：dev=1 临时标注为测试设备
     *
     * @param policy        升级策略
     * @param dev           临时测试设备标识
     * @param augmentedTags 增强后的设备标签
     * @return true 如果匹配
     */
    private boolean matchesDevMode(UpgradePolicy policy, Integer dev,
                                    com.fasterxml.jackson.databind.JsonNode augmentedTags) {
        com.fasterxml.jackson.databind.JsonNode targetTags = policy.getTargetDeviceTags();
        if (targetTags == null || targetTags.isEmpty() || targetTags.isNull()) {
            return true;
        }

        if (targetTags.has("env")) {
            String requiredEnv = targetTags.path("env").asText();
            String actualEnv = augmentedTags == null || augmentedTags.isNull()
                    ? null : augmentedTags.path("env").asText();

            if ("test".equalsIgnoreCase(requiredEnv) || "dev".equalsIgnoreCase(requiredEnv)) {
                boolean isTestDevice = (dev != null && dev == 1)
                        || (actualEnv != null && ("test".equalsIgnoreCase(actualEnv)
                        || "dev".equalsIgnoreCase(actualEnv)));
                return isTestDevice;
            }

            if ("prod".equalsIgnoreCase(requiredEnv) || "production".equalsIgnoreCase(requiredEnv)) {
                if (dev != null && dev == 1) {
                    return false;
                }
                return actualEnv == null
                        || "prod".equalsIgnoreCase(actualEnv)
                        || "production".equalsIgnoreCase(actualEnv);
            }
        }

        return true;
    }

    /**
     * auto 参数匹配：对应策略的 triggerMode
     *
     * @param policy 升级策略
     * @param auto   触发模式（0=手动, 1=自动）
     * @return true 如果匹配
     */
    private boolean matchesTriggerMode(UpgradePolicy policy, Integer auto) {
        String policyTriggerMode = policy.getTriggerMode();
        if (policyTriggerMode == null || policyTriggerMode.isBlank()) {
            return true;
        }

        boolean isAutoCheck = (auto != null && auto == 1);

        return switch (policyTriggerMode.toUpperCase()) {
            case "AUTO" -> isAutoCheck;
            case "MANUAL" -> !isAutoCheck;
            case "BOTH" -> true;
            default -> {
                log.warn("未知的触发模式: {}, 默认匹配", policyTriggerMode);
                yield true;
            }
        };
    }

    /**
     * 灰度发布检查
     * <p>
     * 检查设备是否命中策略的灰度发布
     * </p>
     * <p>业务规则：</p>
     * <ul>
     *   <li>策略灰度比例为 null → 全量匹配（等同于 100%）</li>
     *   <li>策略灰度比例为 0 → 不匹配任何设备</li>
     *   <li>策略灰度比例为 100 → 匹配所有设备</li>
     *   <li>其他比例 → 使用 MurmurHash3 算法判定</li>
     * </ul>
     *
     * @param policy 升级策略
     * @param imei   设备 IMEI
     * @return true 如果命中灰度
     */
    private boolean matchesGrayRelease(UpgradePolicy policy, String imei) {
        Integer grayRate = policy.getGrayRate();
        if (grayRate == null) {
            return true; // 没有灰度限制，全量匹配
        }

        return grayReleaseService.hitsGrayBucket(imei, grayRate);
    }

    /**
     * 配额检查
     * <p>
     * 检查策略配额是否已用尽
     * </p>
     * <p>业务规则：</p>
     * <ul>
     *   <li>策略无配额限制（quota 为 null 或 <= 0）→ 匹配</li>
     *   <li>配额可用 → 匹配（同时占用配额）</li>
     *   <li>配额已用尽 → 不匹配</li>
     * </ul>
     *
     * @param policy 升级策略
     * @return true 如果配额可用
     */
    private boolean matchesQuota(UpgradePolicy policy) {
        Integer quota = policy.getQuota();
        if (quota == null || quota <= 0) {
            return true; // 无配额限制
        }

        boolean allowed = policyQuotaService.checkAndIncrementQuota(policy.getId(), quota);
        if (!allowed) {
            log.debug("策略配额已用尽: policyId={}, quota={}", policy.getId(), quota);
        }
        return allowed;
    }

    /**
     * 设备标签匹配
     * <p>
     * 检查设备标签是否满足策略的 targetDeviceTags 要求
     * </p>
     * <p>业务规则（AND 逻辑）：</p>
     * <ul>
     *   <li>策略没有标签要求 → 匹配</li>
     *   <li>设备没有标签且策略有要求 → 不匹配</li>
     *   <li>设备标签包含策略要求的所有键值对 → 匹配</li>
     * </ul>
     *
     * @param policy    升级策略
     * @param deviceTags 设备标签
     * @return true 如果标签匹配
     */
    private boolean matchesDeviceTags(UpgradePolicy policy, com.fasterxml.jackson.databind.JsonNode deviceTags) {
        return policyMatcher.matchesDeviceTags(policy.getTargetDeviceTags(), deviceTags);
    }

    /**
     * 版本范围匹配
     * <p>
     * 检查设备当前版本是否在策略的源版本列表中
     * </p>
     * <p>业务规则：</p>
     * <ul>
     *   <li>策略无源版本限制 → 匹配</li>
     *   <li>设备版本为 null 且策略有版本限制 → 不匹配</li>
     *   <li>设备版本 ID 在源版本列表中 → 匹配</li>
     * </ul>
     *
     * @param policy    升级策略
     * @param versionId 设备当前版本 ID
     * @return true 如果版本匹配
     */
    private boolean matchesSourceVersion(UpgradePolicy policy, Long versionId) {
        com.fasterxml.jackson.databind.JsonNode sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            return true; // 策略没有版本限制
        }

        if (versionId == null) {
            return false; // 设备版本无法识别，不匹配有限制的策略
        }

        // 检查 versionId 是否在 sourceVersions 数组中
        if (sourceVersions.isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode node : sourceVersions) {
                if (versionId.equals(node.asLong())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 根据 auto 参数调整检查间隔
     * <p>
     * 业务规则：</p>
     * <ul>
     *   <li>auto=0 (手动检查): 用户主动触发，返回较短间隔 (3600秒 = 1小时)</li>
     *   <li>auto=1 (自动检查): 系统自动触发，返回较长间隔 (86400秒 = 24小时)</li>
     *   <li>auto=null: 默认为手动检查</li>
     * </ul>
     * <p>
     * 仅在 responseCheckInterval 为 null 时设置默认值，
     * 保留已设置的间隔（如限流、错误等情况）
     * </p>
     *
     * @param result 检查结果
     * @param auto   自动检查标识：0=手动检查，1=自动检查
     */
    private void adjustCheckInterval(CheckResult result, Integer auto) {
        if (result.getResponseCheckInterval() == null) {
            boolean isAutoCheck = (auto != null && auto == 1);
            int defaultInterval = isAutoCheck ? 86400 : 3600; // 自动: 24小时, 手动: 1小时
            result.setResponseCheckInterval(defaultInterval);
        }
    }

    /**
     * 构建检查结果
     * <p>
     * 使用 UpgradeResponseBuilder 构建完整的响应，包括：
     * <ul>
     *   <li>固件元数据（版本号、文件大小、校验和）</li>
     *   <li>签名下载 URL（集成 SignedUrlService）</li>
     *   <li>多语言发布说明</li>
     *   <li>控制参数（checkInterval, downloadDelay）</li>
     * </ul>
     * </p>
     *
     * @param device    设备信息
     * @param policy    升级策略
     * @param lang      语言代码（可选）
     * @param autoMode  是否自动检查模式（可选）
     * @return 检查结果
     */
    private CheckResult buildCheckResult(Device device, UpgradePolicy policy, String lang, Boolean autoMode) {
        return upgradeResponseBuilder.buildResponse(device, policy, lang, autoMode);
    }

    /**
     * 构建检查结果（无语言参数的简化版本）
     *
     * @param device 设备信息
     * @param policy 升级策略
     * @return 检查结果
     */
    private CheckResult buildCheckResult(Device device, UpgradePolicy policy) {
        return upgradeResponseBuilder.buildResponse(device, policy, null, false);
    }

    /**
     * 检查结果 DTO
     */
    @lombok.Builder
    @lombok.Data
    public static class CheckResult {
        /**
         * 是否有更新
         */
        private Boolean hasUpdate;

        /**
         * 检查决策：UPDATE, NO_UPDATE, RATE_LIMITED, GRAY_MISS
         */
        private String decision;

        /**
         * 目标版本 ID
         */
        private Long targetVersionId;

        /**
         * 目标版本号
         */
        private String targetVersion;

        /**
         * 策略 ID
         */
        private Long policyId;

        /**
         * 建议的下次检查间隔（秒）
         */
        private Integer responseCheckInterval;

        /**
         * 下载延迟（秒）
         */
        private Integer downloadDelay;

        /**
         * 错误码
         */
        private String errorCode;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 扩展数据
         */
        private java.util.Map<String, Object> ext;

        // 静态工厂方法
        public static CheckResult noUpdate() {
            return CheckResult.builder()
                    .hasUpdate(false)
                    .decision("NO_UPDATE")
                    .responseCheckInterval(86400) // 24 小时
                    .build();
        }

        public static CheckResult notFound(String message) {
            return CheckResult.builder()
                    .hasUpdate(false)
                    .decision("DEVICE_NOT_FOUND")
                    .errorMessage(message)
                    .build();
        }

        public static CheckResult error(String message) {
            return CheckResult.builder()
                    .hasUpdate(false)
                    .decision("ERROR")
                    .errorMessage(message)
                    .build();
        }

        /**
         * 创建限流拒绝结果
         *
         * @param message          限流原因
         * @param retryAfterSeconds 重试等待时间（秒）
         * @return CheckResult
         */
        public static CheckResult rateLimited(String message, int retryAfterSeconds) {
            return CheckResult.builder()
                    .hasUpdate(false)
                    .decision("RATE_LIMITED")
                    .errorMessage(message)
                    .responseCheckInterval(retryAfterSeconds)
                    .downloadDelay(retryAfterSeconds)
                    .build();
        }
    }
}
