package com.wewins.fota.infra.metrics;

import com.wewins.fota.cache.bitmap.DeviceActivityBitmapRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 发布设备活跃度指标到 Prometheus。
 */
@Component
public class DeviceActivityMetricsPublisher {

    private final DeviceActivityBitmapRepository bitmapRepository;

    public DeviceActivityMetricsPublisher(
            DeviceActivityBitmapRepository bitmapRepository,
            MeterRegistry meterRegistry) {
        this.bitmapRepository = bitmapRepository;

        Gauge.builder("fota.active.devices.today", this, DeviceActivityMetricsPublisher::readTodayActiveDevices)
                .description("Today's unique active devices (bitmap BITCOUNT)")
                .register(meterRegistry);
    }

    private double readTodayActiveDevices() {
        return bitmapRepository.countActive(LocalDate.now());
    }
}
