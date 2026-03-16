package com.wewins.fota.infra.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceApiHttpServerMetricsFilterConfigurationTest {

    private final MeterFilter filter = new DeviceApiHttpServerMetricsFilterConfiguration()
            .deviceApiHttpServerRequestsOnlyFilter();

    @Test
    void shouldKeepUpgradeCheckMetric() {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.of("uri", "/v1/upgrade/check"), null, null, Meter.Type.TIMER);

        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(id));
    }

    @Test
    void shouldKeepUpgradeReportMetric() {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.of("uri", "/v1/upgrade/report"), null, null, Meter.Type.TIMER);

        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(id));
    }

    @Test
    void shouldMapLegacyUpgradeCheckMetricToNewUri() {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.of("uri", "/fota/version/query"), null, null, Meter.Type.TIMER);

        Meter.Id mapped = filter.map(id);

        assertEquals("/v1/upgrade/check", mapped.getTag("uri"));
        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(mapped));
    }

    @Test
    void shouldDenyNonDeviceHttpMetric() {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.of("uri", "/api/admin/monitor/realtime"), null, null, Meter.Type.TIMER);

        assertEquals(MeterFilterReply.DENY, filter.accept(id));
    }

    @Test
    void shouldIgnoreNonHttpRequestMetric() {
        Meter.Id id = new Meter.Id("fota.device.checks", Tags.empty(), null, null, Meter.Type.COUNTER);

        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(id));
    }

    @Test
    void shouldHandleNullUri() {
        Meter.Id id = new Meter.Id("http.server.requests", Tags.empty(), null, null, Meter.Type.TIMER);

        Meter.Id mapped = filter.map(id);
        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(id));
        assertEquals(id, mapped);
    }
}
