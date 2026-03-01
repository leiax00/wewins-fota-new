package com.wewins.fota.application.upgrade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.wewins.fota.domain.policy.enums.PolicyStatus;

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
 *   <li>生成交分检查间隔建议</li>
 * </ul>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Service
public class UpgradeCheckService {

    private final DeviceRepository deviceRepository;
    private final DeviceCacheRepository deviceCacheService;
    private final DataIntegrityService dataIntegrityService;
    private final UpgradePolicyRepository upgradePolicyRepository;
    private final DeviceRateLimiter deviceRateLimiter;
    private final DeviceActivityBitmapRepository bitmapRepository;
    private final PolicyMatcher policyMatcher;
    private final ProductRepository productRepository;
    private final FirmwareVersionLookupService firmwareVersionLookupService;
    private final GrayReleaseService grayReleaseService;
    private final UpgradeResponseBuilder upgradeResponseBuilder;
    private final UpgradeRequestValidator requestValidator;

    public UpgradeCheckService(
            DeviceRepository deviceRepository,
            @Qualifier("redisDeviceCacheRepository") DeviceCacheRepository deviceCacheService,
            DataIntegrityService dataIntegrityService,
            UpgradePolicyRepository upgradePolicyRepository,
            DeviceRateLimiter deviceRateLimiter,
            @Qualifier("redisDeviceActivityBitmapRepository") DeviceActivityBitmapRepository bitmapRepository,
            PolicyMatcher policyMatcher,
            ProductRepository productRepository,
            FirmwareVersionLookupService firmwareVersionLookupService,
            GrayReleaseService grayReleaseService,
            UpgradeResponseBuilder upgradeResponseBuilder,
            UpgradeRequestValidator requestValidator
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCacheService = deviceCacheService;
        this.dataIntegrityService = dataIntegrityService;
        this.upgradePolicyRepository = upgradePolicyRepository;
        this.deviceRateLimiter = deviceRateLimiter;
        this.bitmapRepository = bitmapRepository;
        this.policyMatcher = policyMatcher;
        this.productRepository = productRepository;
        this.firmwareVersionLookupService = firmwareVersionLookupService;
        this.grayReleaseService = grayReleaseService;
        this.upgradeResponseBuilder = upgradeResponseBuilder;
        this.requestValidator = requestValidator;
    }

    /**
     * 检查设备是否有可用更新（完整参数版本）
     * <p>
     * 新 API 接口，支持完整参数列表
     * </p>
     *
     * @param request 升级检查请求 DTO
     * @return 检查结果
     */
    public CheckResult checkUpgrade(UpgradeCheckReqDTO request) {

        log.debug("开始检查设备更新: request={}", request);

        // 1. 参数校验
        requestValidator.validateRequiredParams(request.getProduct(), request.getImei(), request.getVersion());
        requestValidator.validateAuto(request.getAuto());

        // 2. 限流检查（每分钟最多 10 次请求）
        RateLimitDecision rateLimitDecision = deviceRateLimiter.allow(
                "upgrade:" + request.getImei(),
                10,
                Duration.ofMinutes(1)
        );

        if (!rateLimitDecision.isAllowed()) {
            log.warn("设备请求被限流: imei={}, reason={}", request.getImei(), rateLimitDecision.getReason());
            return CheckResult.rateLimited(
                    "请求过于频繁",
                    (int) (rateLimitDecision.getResetAtEpochSecond() - System.currentTimeMillis() / 1000)
            );
        }

        // 3. 通过产品型号查找产品
        Product product = productRepository.findByModel(request.getProduct()).orElse(null);
        if (product == null) {
            return CheckResult.error("产品型号不存在");
        }

        // 4. 从缓存或数据库加载设备信息
        // 重要：新系统要求设备必须预先导入，设备不存在时拒绝升级（不创建设备）
        Device device = loadDevice(request.getImei());
        if (device == null) {
            return CheckResult.notFound("设备未注册，请联系管理员");
        }

        // 5. 标记设备活跃（使用设备 ID 作为 bitmap 偏移量）
        try {
            bitmapRepository.markActive(LocalDate.now(), device.getId());
            log.debug("标记设备活跃: imei={}, deviceId={}", request.getImei(), device.getId());
        } catch (Exception e) {
            log.error("标记设备活跃失败: imei={}, deviceId={}", request.getImei(), device.getId(), e);
            // 降级处理：记录错误但不中断主流程
        }

        // 6. 查找固件版本 ID（version+tag 组合优先）
        Long versionId = firmwareVersionLookupService.findVersionId(request.getVersion(), request.getTag(), product.getId());
        log.debug("查找固件版本 ID: version={}, tag={}, productId={}, versionId={}",
                request.getVersion(), request.getTag(), product.getId(), versionId);

        // 7. 匹配适用的升级策略（支持 dev、auto 参数）
        List<UpgradePolicy> policies = findApplicablePolicies(device, versionId, request.getDev(), request.getAuto());
        if (policies.isEmpty()) {
            log.debug("未找到适用的升级策略: deviceId={}, versionId={}", device.getId(), versionId);
            return CheckResult.noUpdate();
        }

        // 8. 选择优先级最高的策略
        UpgradePolicy policy = policies.getFirst();

        // 9. 构建响应（支持 lang 和 auto 参数）
        return upgradeResponseBuilder.buildResponse(device, policy, request.getLang(), request.getAuto() == 1);
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
     * 查找适用的升级策略（支持 dev、auto 参数）
     * <p>
     * 根据产品ID、当前版本ID、dev参数、auto参数查找匹配的策略
     * </p>
     * <p>
     * 策略状态匹配规则：
     * <ul>
     *   <li>ACTIVE 状态：对所有设备生效</li>
     *   <li>TESTING、VERIFIED 状态：仅对测试设备生效（dev=1 或设备标签 env=test）</li>
     * </ul>
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

        // 判断是否为测试设备：dev=1 或设备标签中 env=test/dev
        boolean isTestDevice = isTestDevice(device, dev);

        // 按产品拉取生效策略并按优先级降序
        // 测试设备：ACTIVE + VERIFIED + TESTING
        // 普通设备：仅 ACTIVE
        List<UpgradePolicy> policies = upgradePolicyRepository.findEffectiveByProductIdOrderByPriorityDesc(
                device.getProductId(), isTestDevice);

        // 获取设备标签（用于 env 标签匹配）
        JsonNode deviceTags = device.getTags();

        // 过滤策略
        final JsonNode finalTags = augmentTagsWithDevMode(deviceTags, dev);
        final Long finalVersionId = versionId;
        final String imei = device.getImei();

        return policies.stream()
                .filter(policy -> matchesDevMode(policy, dev, finalTags))
                .filter(policy -> matchesTriggerMode(policy, auto))
                .filter(policy -> matchesSourceVersion(policy, finalVersionId))
                .filter(policy -> policyMatcher.matchesDeviceTags(policy.getTargetDeviceTags(), finalTags))
                .filter(policy -> policyMatcher.matchesTimeWindow(policy.getTimeWindow()))
                .filter(policy -> matchesGrayRelease(policy, imei))
                .toList();
    }

    /**
     * 判断设备是否为测试设备
     * <p>
     * 测试设备的定义：
     * <ul>
     *   <li>请求参数 dev=1（临时标记为测试设备）</li>
     *   <li>设备标签中 env=test 或 env=dev</li>
     * </ul>
     * </p>
     *
     * @param device 设备信息
     * @param dev    临时测试设备标识
     * @return true 如果是测试设备
     */
    private boolean isTestDevice(Device device, Integer dev) {
        // dev=1 表示临时标记为测试设备
        if (dev != null && dev == 1) {
            return true;
        }

        // 检查设备标签中的 env 字段
        JsonNode tags = device.getTags();
        if (tags != null && !tags.isNull() && tags.has("env")) {
            String env = tags.path("env").asText();
            return "test".equalsIgnoreCase(env) || "dev".equalsIgnoreCase(env);
        }

        return false;
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
    private JsonNode augmentTagsWithDevMode(
            JsonNode deviceTags, Integer dev) {
        if (dev == null || dev != 1) {
            return deviceTags;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode augmented;

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
                                   JsonNode augmentedTags) {
        JsonNode targetTags = policy.getTargetDeviceTags();
        if (targetTags == null || targetTags.isEmpty() || targetTags.isNull()) {
            return true;
        }

        if (targetTags.has("env")) {
            String requiredEnv = targetTags.path("env").asText();
            String actualEnv = augmentedTags == null || augmentedTags.isNull()
                    ? null : augmentedTags.path("env").asText();

            if ("test".equalsIgnoreCase(requiredEnv) || "dev".equalsIgnoreCase(requiredEnv)) {
                return (dev != null && dev == 1)
                        || (("test".equalsIgnoreCase(actualEnv)
                        || "dev".equalsIgnoreCase(actualEnv)));
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
     * @param policy     升级策略
     * @param deviceTags 设备标签
     * @return true 如果标签匹配
     */
    private boolean matchesDeviceTags(UpgradePolicy policy, JsonNode deviceTags) {
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
        JsonNode sourceVersions = policy.getSourceVersions();
        if (sourceVersions == null || sourceVersions.isEmpty()) {
            return true; // 策略没有版本限制
        }

        if (versionId == null) {
            return false; // 设备版本无法识别，不匹配有限制的策略
        }

        // 检查 versionId 是否在 sourceVersions 数组中
        if (sourceVersions.isArray()) {
            for (JsonNode node : sourceVersions) {
                if (versionId.equals(node.asLong())) {
                    return true;
                }
            }
        }

        return false;
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
     * @param device   设备信息
     * @param policy   升级策略
     * @param lang     语言代码（可选）
     * @param autoMode 是否自动检查模式（可选）
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
}
