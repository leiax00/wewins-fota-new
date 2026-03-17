package com.wewins.fota.infra.cache.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CacheMetricsService {

    private static final String METRIC_PREFIX = "fota.cache.";

    private final MeterRegistry meterRegistry;

    private final Counter productCacheHits;
    private final Counter productCacheMisses;
    private final Counter productCacheInvalidations;

    private final Counter policyCacheHits;
    private final Counter policyCacheMisses;
    private final Counter policyCacheInvalidations;

    private final Counter firmwareCacheHits;
    private final Counter firmwareCacheMisses;
    private final Counter firmwareCacheInvalidations;

    private final Counter firmwareLookupCacheHits;
    private final Counter firmwareLookupCacheMisses;

    private final Counter deviceCacheHits;
    private final Counter deviceCacheMisses;
    private final Counter deviceCacheEvictions;

    private final Counter cacheInvalidationEvents;
    private final Timer cacheInvalidationTimer;

    public CacheMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.productCacheHits = Counter.builder(METRIC_PREFIX + "hits")
                .tag("cache", "product")
                .description("Product cache hits")
                .register(meterRegistry);

        this.productCacheMisses = Counter.builder(METRIC_PREFIX + "misses")
                .tag("cache", "product")
                .description("Product cache misses")
                .register(meterRegistry);

        this.productCacheInvalidations = Counter.builder(METRIC_PREFIX + "invalidations")
                .tag("cache", "product")
                .description("Product cache invalidations")
                .register(meterRegistry);

        this.policyCacheHits = Counter.builder(METRIC_PREFIX + "hits")
                .tag("cache", "policy")
                .description("Policy cache hits")
                .register(meterRegistry);

        this.policyCacheMisses = Counter.builder(METRIC_PREFIX + "misses")
                .tag("cache", "policy")
                .description("Policy cache misses")
                .register(meterRegistry);

        this.policyCacheInvalidations = Counter.builder(METRIC_PREFIX + "invalidations")
                .tag("cache", "policy")
                .description("Policy cache invalidations")
                .register(meterRegistry);

        this.firmwareCacheHits = Counter.builder(METRIC_PREFIX + "hits")
                .tag("cache", "firmware")
                .description("Firmware cache hits")
                .register(meterRegistry);

        this.firmwareCacheMisses = Counter.builder(METRIC_PREFIX + "misses")
                .tag("cache", "firmware")
                .description("Firmware cache misses")
                .register(meterRegistry);

        this.firmwareCacheInvalidations = Counter.builder(METRIC_PREFIX + "invalidations")
                .tag("cache", "firmware")
                .description("Firmware cache invalidations")
                .register(meterRegistry);

        this.firmwareLookupCacheHits = Counter.builder(METRIC_PREFIX + "hits")
                .tag("cache", "firmware_lookup")
                .description("Firmware lookup cache hits")
                .register(meterRegistry);

        this.firmwareLookupCacheMisses = Counter.builder(METRIC_PREFIX + "misses")
                .tag("cache", "firmware_lookup")
                .description("Firmware lookup cache misses")
                .register(meterRegistry);

        this.deviceCacheHits = Counter.builder(METRIC_PREFIX + "hits")
                .tag("cache", "device")
                .description("Device cache hits")
                .register(meterRegistry);

        this.deviceCacheMisses = Counter.builder(METRIC_PREFIX + "misses")
                .tag("cache", "device")
                .description("Device cache misses")
                .register(meterRegistry);

        this.deviceCacheEvictions = Counter.builder(METRIC_PREFIX + "evictions")
                .tag("cache", "device")
                .description("Device cache evictions")
                .register(meterRegistry);

        this.cacheInvalidationEvents = Counter.builder(METRIC_PREFIX + "events")
                .tag("type", "invalidation")
                .description("Cache invalidation events received")
                .register(meterRegistry);

        this.cacheInvalidationTimer = Timer.builder(METRIC_PREFIX + "invalidation.duration")
                .description("Cache invalidation processing time")
                .register(meterRegistry);
    }

    public void recordProductCacheHit() {
        productCacheHits.increment();
    }

    public void recordProductCacheMiss() {
        productCacheMisses.increment();
    }

    public void recordProductCacheInvalidation() {
        productCacheInvalidations.increment();
    }

    public void recordPolicyCacheHit() {
        policyCacheHits.increment();
    }

    public void recordPolicyCacheMiss() {
        policyCacheMisses.increment();
    }

    public void recordPolicyCacheInvalidation() {
        policyCacheInvalidations.increment();
    }

    public void recordFirmwareCacheHit() {
        firmwareCacheHits.increment();
    }

    public void recordFirmwareCacheMiss() {
        firmwareCacheMisses.increment();
    }

    public void recordFirmwareCacheInvalidation() {
        firmwareCacheInvalidations.increment();
    }

    public void recordFirmwareLookupCacheHit() {
        firmwareLookupCacheHits.increment();
    }

    public void recordFirmwareLookupCacheMiss() {
        firmwareLookupCacheMisses.increment();
    }

    public void recordDeviceCacheHit() {
        deviceCacheHits.increment();
    }

    public void recordDeviceCacheMiss() {
        deviceCacheMisses.increment();
    }

    public void recordDeviceCacheEviction() {
        deviceCacheEvictions.increment();
    }

    public void recordCacheInvalidationEvent(String eventType) {
        cacheInvalidationEvents.increment();
        log.debug("Cache invalidation event recorded: type={}", eventType);
    }

    public void recordInvalidationDuration(long durationMs) {
        cacheInvalidationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public <T> T recordWithTimer(java.util.function.Supplier<T> operation) {
        long start = System.currentTimeMillis();
        try {
            return operation.get();
        } finally {
            recordInvalidationDuration(System.currentTimeMillis() - start);
        }
    }
}
