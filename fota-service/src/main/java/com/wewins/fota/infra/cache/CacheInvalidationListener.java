package com.wewins.fota.infra.cache;

import com.wewins.fota.domain.device.cache.DeviceCacheRepository;
import com.wewins.fota.infra.cache.event.ChangeType;
import com.wewins.fota.infra.cache.event.DeviceBatchChangedEvent;
import com.wewins.fota.infra.cache.event.DeviceChangedEvent;
import com.wewins.fota.infra.cache.event.FirmwareChangedEvent;
import com.wewins.fota.infra.cache.event.PolicyChangedEvent;
import com.wewins.fota.infra.cache.event.ProductChangedEvent;
import com.wewins.fota.infra.cache.metrics.CacheMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheInvalidationListener {

    private final ProductCacheInvalidator productCacheInvalidator;
    private final PolicyCacheInvalidator policyCacheInvalidator;
    private final FirmwareCacheInvalidator firmwareCacheInvalidator;
    private final DeviceCacheRepository deviceCacheRepository;
    private final CacheMetricsService cacheMetricsService;

    @EventListener
    @Async
    public void onProductChanged(ProductChangedEvent event) {
        long start = System.currentTimeMillis();
        log.info("收到产品变更事件: productId={}, type={}", event.getProductId(), event.getChangeType());

        if (Objects.requireNonNull(event.getChangeType()) == ChangeType.STATUS_CHANGED) {
            productCacheInvalidator.invalidateOnProductStatusChange(event.getProductId());
        } else {
            productCacheInvalidator.invalidateOnProductChange(event.getProductId());
        }
        cacheMetricsService.recordProductCacheInvalidation();
        cacheMetricsService.recordCacheInvalidationEvent("product");
        cacheMetricsService.recordInvalidationDuration(System.currentTimeMillis() - start);
    }

    @EventListener
    @Async
    public void onPolicyChanged(PolicyChangedEvent event) {
        long start = System.currentTimeMillis();
        log.info("收到策略变更事件: productId={}, policyId={}, type={}",
            event.getProductId(), event.getPolicyId(), event.getChangeType());

        policyCacheInvalidator.invalidateOnPolicyChange(event.getProductId(), event.getPolicyId());
        cacheMetricsService.recordPolicyCacheInvalidation();
        cacheMetricsService.recordCacheInvalidationEvent("policy");
        cacheMetricsService.recordInvalidationDuration(System.currentTimeMillis() - start);
    }

    @EventListener
    @Async
    public void onFirmwareChanged(FirmwareChangedEvent event) {
        long start = System.currentTimeMillis();
        log.info("收到固件变更事件: productId={}, versionId={}, type={}",
            event.getProductId(), event.getVersionId(), event.getChangeType());

        firmwareCacheInvalidator.invalidateOnFirmwarePublish(
            event.getProductId(), event.getVersionId(), event.getVersion(), event.getInternalVersion());
        cacheMetricsService.recordFirmwareCacheInvalidation();
        cacheMetricsService.recordCacheInvalidationEvent("firmware");
        cacheMetricsService.recordInvalidationDuration(System.currentTimeMillis() - start);
    }

    @EventListener
    @Async
    public void onDeviceChanged(DeviceChangedEvent event) {
        log.debug("收到设备变更事件: imei={}, type={}", event.getImei(), event.getChangeType());

        deviceCacheRepository.evict(event.getImei());
        cacheMetricsService.recordDeviceCacheEviction();
        cacheMetricsService.recordCacheInvalidationEvent("device");
    }

    @EventListener
    @Async
    public void onDeviceBatchChanged(DeviceBatchChangedEvent event) {
        long start = System.currentTimeMillis();
        log.info("收到批量设备变更事件: count={}, type={}", event.getImeis().size(), event.getChangeType());

        for (String imei : event.getImeis()) {
            deviceCacheRepository.evict(imei);
        }
        cacheMetricsService.recordDeviceCacheEviction();
        cacheMetricsService.recordCacheInvalidationEvent("device_batch");
        cacheMetricsService.recordInvalidationDuration(System.currentTimeMillis() - start);
    }
}
