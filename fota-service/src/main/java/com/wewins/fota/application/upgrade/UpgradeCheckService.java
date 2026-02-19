package com.wewins.fota.application.upgrade;

import com.wewins.fota.application.validation.DataIntegrityService;
import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import com.wewins.fota.cache.ratelimit.DeviceRateLimiter;
import com.wewins.fota.cache.ratelimit.RateLimitDecision;
import com.wewins.fota.domain.device.cache.DeviceCache;
import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.domain.device.entity.Device;
import com.wewins.fota.domain.device.repository.DeviceRepository;
import com.wewins.fota.domain.policy.entity.UpgradePolicy;
import com.wewins.fota.domain.policy.repository.UpgradePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

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
     * @return 检查结果
     */
    public CheckResult checkUpgrade(String imei) {
        log.debug("开始检查设备更新: imei={}", imei);

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
        Device device = loadDevice(imei);
        if (device == null) {
            log.warn("设备不存在或已软删除: imei={}", imei);
            return CheckResult.notFound("设备不存在");
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
        UpgradePolicy policy = policies.get(0);

        // 6. 构建响应
        return buildCheckResult(device, policy);
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
        // 最小版本：先按产品拉取激活策略并按优先级降序。
        // 后续再补充版本范围、标签、时间窗口过滤。
        return upgradePolicyRepository.findActiveByProductIdOrderByPriorityDesc(device.getProductId());
    }

    /**
     * 构建检查结果
     *
     * @param device    设备信息
     * @param policy    升级策略
     * @return 检查结果
     */
    private CheckResult buildCheckResult(Device device, UpgradePolicy policy) {
        // TODO: 根据策略配置构建响应
        return CheckResult.builder()
                .hasUpdate(true)
                .decision("UPDATE")
                .targetVersionId(policy.getTargetVersionId())
                .responseCheckInterval(3600) // 1 小时
                .build();
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
