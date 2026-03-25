package com.wewins.fota.cdn.spi;

import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;

public interface CdnWorkerConfigProvider {

    CdnWorkerConfig getConfig();

    record CdnWorkerConfig(
            CdnWarmStrategy strategy,
            String workerUrl,
            String warmSecret,
            int defaultTtlSeconds
    ) {
    }
}
