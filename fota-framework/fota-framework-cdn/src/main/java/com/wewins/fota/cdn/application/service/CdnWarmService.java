package com.wewins.fota.cdn.application.service;

import com.wewins.fota.cdn.application.dto.WarmRequest;
import com.wewins.fota.cdn.application.dto.WarmResult;
import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import com.wewins.fota.cdn.infra.client.CdnWarmClient;
import com.wewins.fota.cdn.infra.client.CdnWarmWorkerClient;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * CDN warm-up service.
 */
@Slf4j
public class CdnWarmService {

    private final CdnWarmClient cdnWarmClient;
    private final CdnWarmWorkerClient cdnWarmWorkerClient;
    private final CdnWorkerConfigProvider configProvider;

    public CdnWarmService(
            CdnWarmClient cdnWarmClient,
            CdnWarmWorkerClient cdnWarmWorkerClient,
            CdnWorkerConfigProvider configProvider
    ) {
        this.cdnWarmClient = cdnWarmClient;
        this.cdnWarmWorkerClient = cdnWarmWorkerClient;
        this.configProvider = configProvider;
    }

    /**
     * Warm up URLs from the request.
     *
     * @param request warm-up request containing URLs
     * @return list of warm results
     */
    public List<WarmResult> warm(WarmRequest request) {
        List<String> urls = request.getUrls();
        if (urls == null || urls.isEmpty()) {
            log.warn("No URLs provided for warm-up");
            return List.of();
        }

        log.info("Starting CDN warm-up for {} URLs", urls.size());

        // Perform warm-up
        List<WarmResult> results = cdnWarmClient.warmUrls(urls);
        results.forEach(result -> result.setStrategy(CdnWarmStrategy.SERVER));

        // Log summary
        long successCount = results.stream()
                .filter(r -> r.getStatus() == WarmStatus.SUCCESS)
                .count();
        log.info("CDN warm-up completed: {}/{} successful", successCount, urls.size());

        return results;
    }

    /**
     * Warm up a single URL.
     *
     * @param url the URL to warm up
     * @return warm result
     */
    public WarmResult warm(String url) {
        WarmRequest request = new WarmRequest();
        request.setUrls(List.of(url));
        List<WarmResult> results = warm(request);
        return results.isEmpty() ? null : results.getFirst();
    }

    /**
     * Warm up multiple URLs.
     *
     * @param urls list of URLs to warm up
     * @return list of warm results
     */
    public List<WarmResult> warm(List<String> urls) {
        WarmRequest request = new WarmRequest();
        request.setUrls(urls);
        return warm(request);
    }

    public WarmResult warmViaWorker(String url) {
        if (cdnWarmWorkerClient == null) {
            return WarmResult.builder()
                    .url(url)
                    .strategy(CdnWarmStrategy.WORKER)
                    .status(WarmStatus.FAILED)
                    .errorMessage("Worker 预热未启用")
                    .build();
        }
        return cdnWarmWorkerClient.warmViaWorker(url);
    }

    public boolean isWorkerConfigured() {
        return cdnWarmWorkerClient != null && cdnWarmWorkerClient.isWorkerConfigured();
    }

    public WarmResult warmByConfiguredStrategy(String url) {
        CdnWarmStrategy strategy = resolveStrategy();
        if (strategy == CdnWarmStrategy.WORKER) {
            return warmViaWorker(url);
        }

        WarmResult result = warm(url);
        if (result == null) {
            return WarmResult.builder()
                    .url(url)
                    .strategy(CdnWarmStrategy.SERVER)
                    .status(WarmStatus.FAILED)
                    .errorMessage("预热失败")
                    .build();
        }
        result.setStrategy(CdnWarmStrategy.SERVER);
        return result;
    }

    public CdnWarmStrategy resolveStrategy() {
        if (configProvider == null) {
            return CdnWarmStrategy.SERVER;
        }
        CdnWorkerConfigProvider.CdnWorkerConfig config = configProvider.getConfig();
        return config == null || config.strategy() == null ? CdnWarmStrategy.SERVER : config.strategy();
    }

    /**
     * Shutdown the service and release resources.
     */
    public void shutdown() {
        cdnWarmClient.shutdown();
    }
}
