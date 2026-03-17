package com.wewins.fota.infra.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 默认 HTTP 指标只保留设备 check/report API，避免后台接口污染设备监控口径。
 */
@Configuration
public class DeviceApiHttpServerMetricsFilterConfiguration {

    private static final String HTTP_SERVER_REQUESTS = "http.server.requests";

    @Bean
    public MeterFilter deviceApiHttpServerRequestsOnlyFilter() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter. Id id) {
                if (!HTTP_SERVER_REQUESTS.equals(id.getName())) {
                    return id;
                }
                String uri = id.getTag("uri");
                String canonicalUri = DeviceApiMetricsSupport.canonicalizeDeviceApiUri(uri);
                if (canonicalUri == null || canonicalUri.equals(uri)) {
                    return id;
                }

                List<Tag> retainedTags = id.getTags().stream()
                        .filter(tag -> !"uri".equals(tag.getKey()))
                        .toList();

                return new Meter.Id(
                        id.getName(),
                        Tags.of(retainedTags).and("uri", canonicalUri),
                        id.getBaseUnit(),
                        id.getDescription(),
                        id.getType()
                );
            }

            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (!HTTP_SERVER_REQUESTS.equals(id.getName())) {
                    return MeterFilterReply.NEUTRAL;
                }

                String uri = DeviceApiMetricsSupport.canonicalizeDeviceApiUri(id.getTag("uri"));
                return DeviceApiMetricsSupport.isDeviceApiUri(uri)
                        ? MeterFilterReply.NEUTRAL
                        : MeterFilterReply.DENY;
            }
        };
    }
}
