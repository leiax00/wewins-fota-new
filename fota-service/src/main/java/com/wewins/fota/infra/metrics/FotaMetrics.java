package com.wewins.fota.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * FOTA 自定义业务指标
 * <p>
 * 用于记录设备升级相关的业务指标，供 Prometheus 采集和 Grafana 可视化
 * </p>
 *
 * <h3>指标列表</h3>
 * <ul>
 *   <li>{@code fota_device_checks_total} - 设备升级检查总数</li>
 *   <li>{@code fota_upgrade_events_total} - 升级事件总数</li>
 *   <li>{@code fota_upgrade_duration_seconds} - 升级完成耗时</li>
 *   <li>{@code fota_active_devices} - 活跃设备数</li>
 * </ul>
 */
@Component
public class FotaMetrics {

    private final MeterRegistry registry;

    public FotaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记录设备升级检查
     *
     * @param productModel 产品型号
     * @param decision    检查结果（HAS_UPGRADE, NO_UPGRADE, RATE_LIMITED, MAINTENANCE）
     */
    public void recordDeviceCheck(String productModel, String decision) {
        Counter.builder("fota.device.checks")
                .tags(commonTags(productModel))
                .tag("decision", safeValue(decision))
                .description("Total device upgrade checks")
                .register(registry)
                .increment();
    }

    /**
     * 记录升级事件上报
     *
     * @param productModel 产品型号
     * @param event       事件类型（DL_START, DL_OK, DL_FAIL, UP_OK）
     */
    public void recordUpgradeEvent(String productModel, String event) {
        Counter.builder("fota.upgrade.events")
                .tags(commonTags(productModel))
                .tag("event", safeValue(event))
                .description("Total upgrade events reported by devices")
                .register(registry)
                .increment();
    }

    /**
     * 记录升级完成耗时（从下载开始到升级完成）
     *
     * @param productModel 产品型号
     * @param durationMs  耗时（毫秒）
     */
    public void recordUpgradeDuration(String productModel, long durationMs) {
        Timer.builder("fota.upgrade.duration")
                .tags(commonTags(productModel))
                .description("Time from download start to upgrade complete")
                .publishPercentileHistogram()
                .minimumExpectedValue(java.time.Duration.ofMillis(100))
                .maximumExpectedValue(java.time.Duration.ofHours(1))
                .register(registry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 更新活跃设备计数
     *
     * @param productModel 产品型号
     * @param count       活跃设备数
     */
    public void updateActiveDevices(String productModel, long count) {
        registry.gauge("fota.active.devices",
                commonTags(productModel),
                count);
    }

    /**
     * 记录固件下载请求
     *
     * @param productModel 产品型号
     * @param version     固件版本
     * @param success     是否成功
     */
    public void recordFirmwareDownload(String productModel, String version, boolean success) {
        Counter.builder("fota.firmware.downloads")
                .tags(commonTags(productModel))
                .tag("version", safeValue(version))
                .tag("success", String.valueOf(success))
                .description("Total firmware download requests")
                .register(registry)
                .increment();
    }

    /**
     * 记录策略匹配结果
     *
     * @param productModel 产品型号
     * @param matched     是否匹配到策略
     * @param grayHit     是否命中灰度
     */
    public void recordPolicyMatch(String productModel, boolean matched, boolean grayHit) {
        Counter.builder("fota.policy.matches")
                .tags(commonTags(productModel))
                .tag("matched", String.valueOf(matched))
                .tag("gray_hit", String.valueOf(grayHit))
                .description("Policy match results for device checks")
                .register(registry)
                .increment();
    }

    /**
     * 记录限流事件
     *
     * @param resource 资源名称
     * @param reason   限流原因
     */
    public void recordRateLimited(String resource, String reason) {
        Counter.builder("fota.rate.limited")
                .tag("resource", safeValue(resource))
                .tag("reason", safeValue(reason))
                .description("Rate limited requests")
                .register(registry)
                .increment();
    }

    /**
     * 记录熔断事件
     *
     * @param resource 资源名称
     * @param state    熔断状态（OPEN, HALF_OPEN, CLOSED）
     */
    public void recordCircuitBreaker(String resource, String state) {
        Counter.builder("fota.circuit.breaker")
                .tag("resource", safeValue(resource))
                .tag("state", safeValue(state))
                .description("Circuit breaker state changes")
                .register(registry)
                .increment();
    }

    private Tags commonTags(String productModel) {
        return Tags.of("product", safeValue(productModel));
    }

    private String safeValue(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }
}
