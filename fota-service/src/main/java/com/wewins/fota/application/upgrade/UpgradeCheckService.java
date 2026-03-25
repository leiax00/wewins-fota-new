package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.load.DynamicIntervalService;
import com.wewins.fota.application.reporting.DeviceCheckLogBuilder;
import com.wewins.fota.application.upgrade.dto.CheckContext;
import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.common.enums.CheckMode;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.model.vo.DeviceCache;
import com.wewins.fota.domain.device.model.vo.DeviceVersionPart;
import com.wewins.fota.domain.device.model.vo.DeviceVersionParts;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.device.service.DeviceInfoUpdateGateway;
import com.wewins.fota.domain.firmware.model.entity.FirmwareVersion;
import com.wewins.fota.domain.firmware.repository.FirmwareVersionRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import com.wewins.fota.infra.metrics.FotaMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private final UpgradePolicyRepository upgradePolicyRepository;
    private final DeviceRateLimiter deviceRateLimiter;
    private final DeviceActivityBitmapRepository bitmapRepository;
    private final PolicyMatcher policyMatcher;
    private final ProductRepository productRepository;
    private final FirmwareVersionLookupService firmwareVersionLookupService;
    private final GrayReleaseService grayReleaseService;
    private final UpgradeResponseBuilder upgradeResponseBuilder;
    private final UpgradeRequestValidator requestValidator;
    private final CheckLogGateway checkLogGateway;
    private final DeviceCheckLogBuilder checkLogBuilder;
    private final DeviceInfoUpdateGateway deviceInfoUpdateGateway;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final DynamicIntervalService dynamicIntervalService;
    private final FotaMetrics fotaMetrics;

    public UpgradeCheckService(
            DeviceRepository deviceRepository,
            @Qualifier("redisDeviceCacheRepository") DeviceCacheRepository deviceCacheService,
            UpgradePolicyRepository upgradePolicyRepository,
            DeviceRateLimiter deviceRateLimiter,
            @Qualifier("redisDeviceActivityBitmapRepository") DeviceActivityBitmapRepository bitmapRepository,
            PolicyMatcher policyMatcher,
            ProductRepository productRepository,
            FirmwareVersionLookupService firmwareVersionLookupService,
            GrayReleaseService grayReleaseService,
            UpgradeResponseBuilder upgradeResponseBuilder,
            UpgradeRequestValidator requestValidator,
            CheckLogGateway checkLogGateway,
            DeviceCheckLogBuilder checkLogBuilder,
            DeviceInfoUpdateGateway deviceInfoUpdateGateway,
            FirmwareVersionRepository firmwareVersionRepository,
            DynamicIntervalService dynamicIntervalService,
            FotaMetrics fotaMetrics) {
        this.deviceRepository = deviceRepository;
        this.deviceCacheService = deviceCacheService;
        this.upgradePolicyRepository = upgradePolicyRepository;
        this.deviceRateLimiter = deviceRateLimiter;
        this.bitmapRepository = bitmapRepository;
        this.policyMatcher = policyMatcher;
        this.productRepository = productRepository;
        this.firmwareVersionLookupService = firmwareVersionLookupService;
        this.grayReleaseService = grayReleaseService;
        this.upgradeResponseBuilder = upgradeResponseBuilder;
        this.requestValidator = requestValidator;
        this.checkLogGateway = checkLogGateway;
        this.checkLogBuilder = checkLogBuilder;
        this.deviceInfoUpdateGateway = deviceInfoUpdateGateway;
        this.firmwareVersionRepository = firmwareVersionRepository;
        this.dynamicIntervalService = dynamicIntervalService;
        this.fotaMetrics = fotaMetrics;
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
        return checkUpgrade(request, null);
    }

    /**
     * 检查设备是否有可用更新（带日志上下文）
     * <p>
     * 新 API 接口，支持完整参数列表和日志记录
     * </p>
     *
     * @param request    升级检查请求 DTO
     * @param logContext HTTP 请求上下文（用于日志记录）
     * @return 检查结果
     */
    public CheckResult checkUpgrade(UpgradeCheckReqDTO request, CheckLogContext logContext) {

        log.debug("开始检查设备更新: request={}", request);

        CheckContext ctx = CheckContext.create(request, logContext);

        try {
            validateRequest(request);

            checkRateLimit(ctx);
            if (ctx.hasResult()) {
                return ctx.getResult();
            }

            findProduct(ctx);
            if (ctx.hasResult()) {
                return ctx.getResult();
            }

            loadDevice(ctx);
            if (ctx.hasResult()) {
                return ctx.getResult();
            }

            markDeviceActive(ctx);

            findDeviceCurrentFirmware(ctx);

            findApplicablePolicies(ctx);
            if (ctx.hasResult()) {
                return ctx.getResult();
            }

            buildResponse(ctx);

            return ctx.getResult();
        } finally {
            recordCheckLog(ctx);
            recordBusinessMetrics(ctx);
            checkAndSendDeviceInfoUpdate(ctx);
        }
    }

    private void validateRequest(UpgradeCheckReqDTO request) {
        requestValidator.validateRequiredParams(request.getProduct(), request.getImei(), request.getVersion());
        requestValidator.validateCheckMode(request.getCheckMode());
    }

    private void checkRateLimit(CheckContext ctx) {
        RateLimitDecision decision = deviceRateLimiter.allow(
                "upgrade:" + ctx.imei(),
                10,
                Duration.ofMinutes(1)
        );
        ctx.setRateLimitDecision(decision);

        if (!decision.isAllowed()) {
            log.warn("设备请求被限流: imei={}, reason={}", ctx.imei(), decision.getReason());
            ctx.setResult(CheckResult.rateLimited(
                    ctx.getRequestId(),
                    "请求过于频繁",
                    -1
            ));
        }
    }

    private void findProduct(CheckContext ctx) {
        Product product = productRepository.findByModel(ctx.productModel()).orElse(null);
        ctx.setProduct(product);

        if (product == null) {
            ctx.setResult(CheckResult.error(ctx.getRequestId(), "产品型号不存在"));
        }
    }

    private void loadDevice(CheckContext ctx) {
        Device device = loadDevice(ctx.imei());
        ctx.setDevice(device);

        if (device == null) {
            ctx.setResult(CheckResult.notFound(ctx.getRequestId(), "设备未注册，请联系管理员"));
        } else if (!device.getProductId().equals(ctx.productId())) {
            ctx.setResult(CheckResult.error(ctx.getRequestId(), "设备归属错误"));
        }
    }

    private void markDeviceActive(CheckContext ctx) {
        try {
            bitmapRepository.markActive(LocalDateTime.now().toLocalDate(), ctx.deviceId());
            log.debug("标记设备活跃: imei={}, deviceId={}", ctx.imei(), ctx.deviceId());
        } catch (Exception e) {
            log.error("标记设备活跃失败: imei={}, deviceId={}", ctx.imei(), ctx.deviceId(), e);
        }
    }

    private void findDeviceCurrentFirmware(CheckContext ctx) {
        FirmwareVersion currentFirmware = firmwareVersionLookupService.findMatchedFirmwareVersion(
                ctx.version(),
                ctx.internalVersion(),
                ctx.productId(),
                ctx.getDevice() != null ? ctx.getDevice().getTags() : null
        ).orElse(null);
        ctx.setCurrentFirmware(currentFirmware);
        log.debug("查找当前固件: version={}, tag={}, productId={}, versionId={}",
                ctx.version(),
                ctx.getRequest().getTag(),
                ctx.productId(),
                currentFirmware != null ? currentFirmware.getId() : null);
    }

    private void findApplicablePolicies(CheckContext ctx) {
        List<UpgradePolicy> policies = findApplicablePolicies(
                ctx.getDevice(), 
                ctx.currentVersionId(),
                ctx.getRequest().getDev(), 
                ctx.getRequest().getCheckMode()
        );
        
        if (policies.isEmpty()) {
            log.debug("未找到适用的升级策略: deviceId={}, versionId={}", ctx.deviceId(), ctx.currentVersionId());
            CheckResult result = CheckResult.noUpdate(ctx.getRequestId());
            result.setCheckInterval(dynamicIntervalService.calculateCheckInterval(ctx.productId()));
            ctx.setResult(result);
            return;
        }

        ctx.setMatchedPolicy(policies.getFirst());
    }

    private void buildResponse(CheckContext ctx) {
        CheckResult result = upgradeResponseBuilder.buildResponse(
                ctx.getDevice(),
                ctx.getMatchedPolicy(),
                ctx.getRequestId(),
                ctx.getRequest().getLang()
        );
        ctx.setResult(result);
    }

    private void checkAndSendDeviceInfoUpdate(CheckContext ctx) {
        if (!ctx.isNormalResult()) {
            return;
        }

        try {
            Device device = ctx.getDevice();
            FirmwareVersion currentFirmware = ctx.getCurrentFirmware();
            Long versionId = ctx.currentVersionId();
            String requestId = ctx.getRequestId();
            LocalDateTime now = LocalDateTime.now();
            boolean isFirstOnline = ctx.isFirstOnline();

            DeviceInfoUpdateMessage.DeviceInfoUpdateMessageBuilder messageBuilder =
                    DeviceInfoUpdateMessage.builder()
                            .correlationId(requestId)
                            .deviceId(device.getId())
                            .imei(device.getImei())
                            .productId(device.getProductId())
                            .accessTime(now);

            if (currentFirmware != null) {
                String partName = resolvePartName(currentFirmware);
                DeviceVersionPart oldPart = findPart(device.getVersionParts(), partName);
                if (isDiffPart(versionId, oldPart)) {
                    messageBuilder.currentVersionParts(Map.of(
                            partName,
                            DeviceVersionPart.builder()
                                    .versionId(versionId)
                                    .version(currentFirmware.getVersion())
                                    .internalVersion(currentFirmware.getInternalVersion())
                                    .updatedAt(now)
                                    .build()
                    ));
                }

                DeviceVersionPart oldInitialPart = findPart(device.getInitialVersionParts(), partName);
                if (isFirstOnline || isDiffPart(versionId, oldInitialPart)) {
                    messageBuilder.initialVersionParts(Map.of(
                            partName,
                            DeviceVersionPart.builder()
                                    .versionId(versionId)
                                    .version(currentFirmware.getVersion())
                                    .internalVersion(currentFirmware.getInternalVersion())
                                    .updatedAt(now)
                                    .build()
                    ));
                }
            }

            if (isFirstOnline) {
                messageBuilder.isFirstOnline(true);
            }

            DeviceInfoUpdateMessage message = messageBuilder.build();
            deviceInfoUpdateGateway.accept(message);
            log.debug("设备信息更新消息已发送: imei={}, requestId={}", device.getImei(), requestId);
        } catch (Exception e) {
            log.error("设备信息更新消息发送失败: imei={}, requestId={}", ctx.imei(), ctx.getRequestId(), e);
        }
    }

    private boolean isDiffPart(Long versionId, DeviceVersionPart part) {
        return part == null || part.getVersionId() == null || !part.getVersionId().equals(versionId);
    }

    private String resolvePartName(FirmwareVersion firmwareVersion) {
        if (firmwareVersion == null || firmwareVersion.getMeta() == null) {
            return "main";
        }
        Object partObj = firmwareVersion.getMeta().get("part");
        if (partObj instanceof String part && !part.isBlank()) {
            return part;
        }
        return "main";
    }

    private DeviceVersionPart findPart(DeviceVersionParts versionParts, String partName) {
        if (versionParts == null || versionParts.getParts() == null) {
            return null;
        }
        return versionParts.getParts().get(partName);
    }

    private boolean isVersionMatch(Device device, Long versionId) {
        if (device.getVersionParts() == null) {
            return false;
        }
        
        return device.getVersionParts().matchAny(versionId);
    }

    private void recordCheckLog(CheckContext ctx) {
        if (!ctx.hasResult()) {
            return;
        }
        try {
            DeviceCheckLog checkLog = checkLogBuilder.build(
                    ctx.getRequest(), 
                    ctx.getDevice(), 
                    ctx.getMatchedPolicy(), 
                    ctx.getResult(), 
                    ctx.getLogContext()
            );
            checkLogGateway.accept(checkLog);
        } catch (Exception e) {
            log.error("检查日志记录失败: imei={}, requestId={}",
                    ctx.imei(),
                    ctx.getRequestId(), e);
        }
    }

    private void recordBusinessMetrics(CheckContext ctx) {
        if (!ctx.hasResult()) {
            return;
        }
        String imei = ctx.imei();
        String productModel = ctx.getProduct() != null ? ctx.getProduct().getModel() : ctx.productModel();
        String decision = ctx.getResult().getDecision() != null ? ctx.getResult().getDecision().name() : "UNKNOWN";
        fotaMetrics.recordDeviceCheck(productModel, decision);

        UpgradePolicy policy = ctx.getMatchedPolicy();
        if (policy != null) {
            // 判断是否命中灰度
            Integer grayRate = policy.getGrayRate();
            boolean grayHit = grayRate != null && grayRate > 0 && grayReleaseService.hitsGrayBucket(imei, grayRate);
            fotaMetrics.recordPolicyMatch(productModel, true, grayHit);
        } else {
            fotaMetrics.recordPolicyMatch(productModel, false, false);
        }

        if (ctx.getRateLimitDecision() != null && !ctx.getRateLimitDecision().isAllowed()) {
            fotaMetrics.recordRateLimited("device-rate-limit", ctx.getRateLimitDecision().getReason());
        }
    }

    /**
     * 从缓存或数据库加载设备信息
     *
     * @param imei 设备 IMEI
     * @return 设备信息，如果不存在则返回 null
     */
    private Device loadDevice(String imei) {
        CacheLookupResult<DeviceCache> lookup = deviceCacheService.get(imei);
        DeviceCache cached = lookup.value();
        if (lookup.hit() && cached == null) {
            log.debug("设备负缓存命中: imei={}", imei);
            return null;
        }

        if (cached != null) {
            log.debug("设备缓存命中: imei={}, deviceId={}", imei, cached.getDeviceId());
            Device device = new Device();
            device.setId(cached.getDeviceId());
            device.setImei(imei);
            device.setProductId(cached.getProductId());
            device.setVersionParts(cached.getVersionParts());
            device.setTags(cached.getTags());
            device.setImportBatchId(cached.getImportBatchId());
            return device;
        }

        Device device = deviceRepository.findByImei(imei).orElse(null);
        if (device != null) {
            DeviceCache cache = DeviceCache.builder()
                    .deviceId(device.getId())
                    .productId(device.getProductId())
                    .versionParts(device.getVersionParts())
                    .tags(device.getTags())
                    .importBatchId(device.getImportBatchId())
                    .build();
            deviceCacheService.put(imei, cache);
            log.debug("设备缓存已写入: imei={}, deviceId={}", imei, device.getId());
        } else {
            deviceCacheService.putNotFound(imei);
        }

        return device;
    }

    /**
     * 查找适用的升级策略（支持 dev、checkMode 参数）
     * <p>
     * 根据产品ID、当前版本ID、dev参数、checkMode参数查找匹配的策略
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
     * @param checkMode 触发模式
     * @return 适用的策略列表（按优先级降序）
     */
    private List<UpgradePolicy> findApplicablePolicies(Device device, Long versionId, Integer dev, CheckMode checkMode) {
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

        // 获取设备标签（用于 env 标签匹配和 targetMode=DEVICE_TAGS）
        Map<String, String> deviceTags = device.getTags();

        // 过滤策略
        final Map<String, String> finalTags = augmentTagsWithDevMode(deviceTags, dev);
        final Long finalVersionId = versionId;
        final String imei = device.getImei();
        final Long batchId = device.getImportBatchId();

        return policies.stream()
                .filter(policy -> matchesDevMode(policy, isTestDevice))
                .filter(policy -> matchesTriggerMode(policy, checkMode))
                .filter(policy -> matchesSourceVersion(policy, finalVersionId))
                .filter(policy -> policyMatcher.matchesTargetMode(policy, imei, batchId, finalTags))
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
        Map<String, String> tags = device.getTags();
        if (tags != null) {
            String env = tags.get("env");
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
    private Map<String, String> augmentTagsWithDevMode(
            Map<String, String> deviceTags, Integer dev) {
        if (dev == null || dev != 1) {
            return deviceTags;
        }

        try {
            java.util.LinkedHashMap<String, String> augmented = new java.util.LinkedHashMap<>();
            if (deviceTags != null) {
                augmented.putAll(deviceTags);
            }
            augmented.put("env", "test");
            return augmented;
        } catch (Exception e) {
            log.warn("增强设备标签失败，使用原始标签", e);
            return deviceTags;
        }
    }

    /**
     * dev 参数匹配：dev=1 临时标注为测试设备
     *
     * @param policy       升级策略
     * @param isTestDevice 是否是测试设备
     * @return true 如果匹配
     */
    private boolean matchesDevMode(UpgradePolicy policy, boolean isTestDevice) {
        PolicyStatus status = policy.getStatus();
        return isTestDevice || status == PolicyStatus.ACTIVE;
    }

    /**
     * checkMode 参数匹配：对应策略的 triggerMode
     *
     * @param policy    升级策略
     * @param checkMode 触发模式
     * @return true 如果匹配
     */
    private boolean matchesTriggerMode(UpgradePolicy policy, CheckMode checkMode) {
        TriggerMode triggerMode = policy.getTriggerMode();
        if (triggerMode == null) {
            return true;
        }

        boolean isAutoCheck = checkMode == CheckMode.AUTO;
        return isAutoCheck ? triggerMode.allowsAuto() : triggerMode.allowsManual();
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
        if (grayRate == null || grayRate == 100) {
            return true; // 没有灰度限制，全量匹配
        }

        return grayReleaseService.hitsGrayBucket(imei, grayRate);
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
        if (policy.getSourceVersions() == null || policy.getSourceVersions().isEmpty()) {
            return true; // 策略没有版本限制
        }

        if (versionId == null) {
            return false; // 设备版本无法识别，不匹配有限制的策略
        }

        return policy.getSourceVersions().contains(versionId);
    }

}
