package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.reporting.DeviceCheckLogBuilder;
import com.wewins.fota.application.upgrade.dto.CheckLogContext;
import com.wewins.fota.application.upgrade.dto.CheckResult;
import com.wewins.fota.application.upgrade.dto.UpgradeCheckReqDTO;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.common.enums.CheckMode;
import com.wewins.fota.common.util.IdGenerator;
import com.wewins.fota.domain.base.vo.CacheLookupResult;
import com.wewins.fota.domain.device.model.vo.DeviceCache;
import com.wewins.fota.domain.device.repository.DeviceCacheRepository;
import com.wewins.fota.domain.device.model.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.policy.model.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.model.enums.PolicyStatus;
import com.wewins.fota.domain.policy.model.enums.TriggerMode;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import com.wewins.fota.domain.product.model.entity.Product;
import com.wewins.fota.domain.product.repository.ProductRepository;
import com.wewins.fota.domain.reporting.model.aggregate.DeviceCheckLog;
import com.wewins.fota.domain.reporting.service.CheckLogGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
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
            DeviceCheckLogBuilder checkLogBuilder
    ) {
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

        // 生成请求唯一标识（用于关联 check 和 report）
        String requestId = IdGenerator.simpleUUID();

        // 用于日志记录的中间状态
        Device device = null;
        UpgradePolicy matchedPolicy = null;
        CheckResult result = null;

        try {
            // 1. 参数校验
            requestValidator.validateRequiredParams(request.getProduct(), request.getImei(), request.getVersion());
            requestValidator.validateCheckMode(request.getCheckMode());

            // 2. 限流检查（每分钟最多 10 次请求）
            RateLimitDecision rateLimitDecision = deviceRateLimiter.allow(
                    "upgrade:" + request.getImei(),
                    10,
                    Duration.ofMinutes(1)
            );

            if (!rateLimitDecision.isAllowed()) {
                log.warn("设备请求被限流: imei={}, reason={}", request.getImei(), rateLimitDecision.getReason());
                result = CheckResult.rateLimited(
                        requestId,
                        "请求过于频繁",
                        (int) (rateLimitDecision.getResetAtEpochSecond() - System.currentTimeMillis() / 1000)
                );
                return result;
            }

            // 3. 通过产品型号查找产品
            Product product = productRepository.findByModel(request.getProduct()).orElse(null);
            if (product == null) {
                result = CheckResult.error(requestId, "产品型号不存在");
                return result;
            }

            // 4. 从缓存或数据库加载设备信息
            // 重要：新系统要求设备必须预先导入，设备不存在时拒绝升级（不创建设备）
            device = loadDevice(request.getImei());
            if (device == null) {
                result = CheckResult.notFound(requestId, "设备未注册，请联系管理员");
                return result;
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

            // 7. 匹配适用的升级策略（支持 dev、checkMode 参数）
            List<UpgradePolicy> policies = findApplicablePolicies(device, versionId, request.getDev(), request.getCheckMode());
            if (policies.isEmpty()) {
                log.debug("未找到适用的升级策略: deviceId={}, versionId={}", device.getId(), versionId);
                result = CheckResult.noUpdate(requestId);
                return result;
            }

            // 8. 选择优先级最高的策略
            matchedPolicy = policies.getFirst();

            // 9. 构建响应（支持 lang 和 checkMode 参数）
            result = upgradeResponseBuilder.buildResponse(device, matchedPolicy, requestId, request.getLang(), request.getCheckMode() == CheckMode.AUTO);

            return result;
        } finally {
            // 记录检查日志（异步，不阻塞主流程）
            recordCheckLog(request, device, matchedPolicy, result, logContext);
        }
    }

    /**
     * 记录检查日志
     * <p>
     * 使用 try-catch 确保日志记录失败不影响主流程
     * </p>
     *
     * @param request    升级检查请求
     * @param device     设备信息（可能为 null）
     * @param policy     匹配的策略（可能为 null）
     * @param result     检查结果
     * @param logContext HTTP 请求上下文
     */
    private void recordCheckLog(UpgradeCheckReqDTO request, Device device, UpgradePolicy policy, CheckResult result, CheckLogContext logContext) {
        try {
            DeviceCheckLog checkLog = checkLogBuilder.build(request, device, policy, result, logContext);
            checkLogGateway.accept(checkLog);
        } catch (Exception e) {
            log.error("检查日志记录失败: imei={}, requestId={}",
                    request != null ? request.getImei() : null,
                    result != null ? result.getRequestId() : null, e);
            // 降级处理：日志记录失败不影响主流程
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
