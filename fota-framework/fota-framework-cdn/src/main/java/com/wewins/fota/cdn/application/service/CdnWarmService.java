package com.wewins.fota.cdn.application.service;

import com.wewins.fota.cdn.application.dto.WarmRequest;
import com.wewins.fota.cdn.application.dto.WarmResult;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import com.wewins.fota.cdn.infra.client.CdnWarmClient;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * CDN warm-up service.
 */
@Slf4j
public class CdnWarmService {

    private final CdnWarmClient cdnWarmClient;

    public CdnWarmService(CdnWarmClient cdnWarmClient) {
        this.cdnWarmClient = cdnWarmClient;
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

    /**
     * Shutdown the service and release resources.
     */
    public void shutdown() {
        cdnWarmClient.shutdown();
    }
}
