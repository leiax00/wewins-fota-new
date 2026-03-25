package com.wewins.fota.cdn.infra.client;

import com.wewins.fota.cdn.application.dto.WarmResult;
import com.wewins.fota.cdn.config.CdnWarmProperties;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * CDN HTTP client for warm-up requests.
 */
@Slf4j
public class CdnWarmClient {

    private static final long LARGE_FILE_THRESHOLD = 50 * 1024 * 1024L; // 50MB
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024 * 1024; // 10GB max
    private static final String CF_CACHE_STATUS_HEADER = "CF-Cache-Status";
    private static final String CF_RAY_HEADER = "CF-RAY";
    private static final String CF_ACCEPT_RANGES_HEADER = "Accept-Ranges";
    private static final int DEFAULT_RETRY_COUNT = 2;

    private final CdnWarmProperties properties;
    private final RestTemplate restTemplate;
    private final ExecutorService executorService;

    public CdnWarmClient(CdnWarmProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.executorService = Executors.newFixedThreadPool(properties.getChunkConcurrency());
    }

    /**
     * Warm up a single URL.
     *
     * @param url the URL to warm up
     * @return warm result
     */
    public WarmResult warmUrl(String url) {
        // Validate URL first
        if (!isValidUrl(url)) {
            log.error("Invalid URL: {}", url);
            return WarmResult.builder()
                    .url(url)
                    .status(WarmStatus.FAILED)
                    .errorMessage("Invalid URL format")
                    .build();
        }
        log.info("Start to warm-up: {}", url);

        // Retry logic
        int retryCount = properties.getRetryCount() > 0 ? properties.getRetryCount() : DEFAULT_RETRY_COUNT;
        Exception lastException = null;
        String lastFailureMessage = null;

        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                WarmResult result = doWarmUrl(url);
                if (result.getStatus() == WarmStatus.SUCCESS) {
                    return result;
                }
                lastFailureMessage = result.getErrorMessage();
                if (lastFailureMessage == null || lastFailureMessage.isBlank()) {
                    lastFailureMessage = "Warm-up failed without explicit error detail";
                }
                if (attempt < retryCount) {
                    log.warn("Attempt {}/{} returned failed result for URL {}: {}",
                            attempt, retryCount, url, lastFailureMessage);
                    try {
                        Thread.sleep(500L * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed for URL {}: {}", attempt, retryCount, url, e.getMessage());
                if (attempt < retryCount) {
                    try {
                        Thread.sleep(500L * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Failed to warm URL after {} attempts: {}", retryCount, url, lastException);
        return WarmResult.builder()
                .url(url)
                .status(WarmStatus.FAILED)
                .errorMessage(resolveErrorMessage(lastException, lastFailureMessage))
                .build();
    }

    /**
     * Warm up multiple URLs.
     *
     * @param urls list of URLs to warm up
     * @return list of warm results
     */
    public List<WarmResult> warmUrls(List<String> urls) {
        long timeoutSeconds = properties.getTimeoutSeconds();

        List<CompletableFuture<WarmResult>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> warmUrl(url), executorService)
                        .completeOnTimeout(
                                WarmResult.builder()
                                        .url(url)
                                        .status(WarmStatus.FAILED)
                                        .errorMessage("Request timeout after " + timeoutSeconds + " seconds")
                                        .build(),
                                timeoutSeconds,
                                TimeUnit.SECONDS
                        ))
                .toList();

        // Wait for all futures to complete (no extra timeout needed, each future handles its own)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private WarmResult doWarmUrl(String url) {
        String fullUrl = buildFullUrl(url);

        // First, make a HEAD request to get file size
        long fileSize = getFileSize(fullUrl);

        // Check if file is too large
        if (fileSize > MAX_FILE_SIZE) {
            log.warn("File too large to warm: {} ({}MB)", fullUrl, fileSize / (1024 * 1024));
            return WarmResult.builder()
                    .url(fullUrl)
                    .status(WarmStatus.FAILED)
                    .errorMessage("File size exceeds maximum allowed (" + MAX_FILE_SIZE / (1024 * 1024 * 1024) + "GB)")
                    .build();
        }

        if (fileSize > LARGE_FILE_THRESHOLD) {
            // Check if CDN supports Range requests for large files
            if (!supportsRange(fullUrl)) {
                log.warn("CDN does not support Range requests, falling back to normal warm-up: {}", fullUrl);
                return warmNormalFile(fullUrl);
            }
            return warmLargeFile(fullUrl, fileSize);
        } else {
            return warmNormalFile(fullUrl);
        }
    }

    /**
     * Check if the CDN supports Range requests.
     */
    private boolean supportsRange(String url) {
        try {
            HttpHeaders headers = restTemplate.headForHeaders(url);
            String acceptRanges = headers.getFirst(CF_ACCEPT_RANGES_HEADER);
            return "bytes".equals(acceptRanges);
        } catch (Exception e) {
            log.warn("Failed to check Range support for {}, assuming not supported: {}", url, e.getMessage());
            return false;
        }
    }

    private long getFileSize(String url) {
        try {
            HttpHeaders headers = restTemplate.headForHeaders(url);
            String contentLength = headers.getFirst(HttpHeaders.CONTENT_LENGTH);
            if (contentLength != null) {
                return Long.parseLong(contentLength);
            }
        } catch (Exception e) {
            log.warn("Failed to get file size for {}, assuming small file: {}", url, e.getMessage());
        }
        return 0;
    }

    private WarmResult warmNormalFile(String url) {
        // Use byte array to avoid text decoding issues with binary firmware files
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                byte[].class
        );

        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String cacheStatus = response.getHeaders().getFirst(CF_CACHE_STATUS_HEADER);
        String cfRay = response.getHeaders().getFirst(CF_RAY_HEADER);

        WarmStatus warmStatus = statusCode.is2xxSuccessful() ? WarmStatus.SUCCESS : WarmStatus.FAILED;
        String errorMessage = warmStatus == WarmStatus.SUCCESS
                ? null
                : "HTTP status " + statusCode.value() + " during warm-up";

        return WarmResult.builder()
                .url(url)
                .status(warmStatus)
                .errorMessage(errorMessage)
                .cacheStatus(cacheStatus)
                .pop(extractPopFromCfRay(cfRay))
                .cfRay(cfRay)
                .chunked(false)
                .build();
    }

    private WarmResult warmLargeFile(String url, long fileSize) {
        int chunkSizeBytes = properties.getChunkSizeMb() * 1024 * 1024;
        // Use long to avoid overflow, then cast to int safely
        long totalChunksLong = (fileSize + chunkSizeBytes - 1) / chunkSizeBytes;
        if (totalChunksLong > Integer.MAX_VALUE) {
            log.error("Too many chunks required for file: {}", totalChunksLong);
            return WarmResult.builder()
                    .url(url)
                    .status(WarmStatus.FAILED)
                    .errorMessage("File too large, too many chunks required")
                    .build();
        }
        int totalChunks = (int) totalChunksLong;

        log.info("Warming large file: {} ({}MB) in {} chunks (max {} concurrent)",
                url, fileSize / (1024 * 1024), totalChunks, properties.getChunkConcurrency());

        // Semaphore to limit concurrent chunk requests
        Semaphore chunkSemaphore = new Semaphore(properties.getChunkConcurrency());
        List<CompletableFuture<ChunkResult>> futures = new ArrayList<>();

        for (int i = 0; i < totalChunks; i++) {
            try {
                // Wait for a permit before submitting the next chunk request
                chunkSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            final long start = i * (long) chunkSizeBytes;
            final long end = Math.min(start + chunkSizeBytes, fileSize) - 1;

            CompletableFuture<ChunkResult> future = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return requestChunk(url, start, end);
                        } finally {
                            // Release permit after chunk completes
                            chunkSemaphore.release();
                        }
                    },
                    executorService
            ).exceptionally(ex -> {
                log.error("Failed to request chunk {}-{} for {}", start, end, url, ex);
                return new ChunkResult(false, null, null, ex.getMessage());
            });
            futures.add(future);
        }

        // Wait for all chunks to complete with timeout
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allFutures.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Timeout waiting for chunk requests to complete for {}", url, e);
        }

        // Collect results (join won't throw since we added exceptionally handler)
        List<ChunkResult> chunkResults = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Check if all chunks succeeded
        boolean allSuccess = chunkResults.stream().allMatch(ChunkResult::success);
        String cacheStatus = chunkResults.stream()
                .map(ChunkResult::cacheStatus)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String cfRay = chunkResults.stream()
                .map(ChunkResult::cfRay)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        String chunkErrorMessage = chunkResults.stream()
                .map(ChunkResult::errorMessage)
                .filter(Objects::nonNull)
                .filter(msg -> !msg.isBlank())
                .findFirst()
                .orElse(null);

        return WarmResult.builder()
                .url(url)
                .status(allSuccess ? WarmStatus.SUCCESS : WarmStatus.FAILED)
                .errorMessage(allSuccess ? null : firstNonBlank(chunkErrorMessage, "One or more chunk requests failed"))
                .cacheStatus(cacheStatus)
                .pop(extractPopFromCfRay(cfRay))
                .cfRay(cfRay)
                .chunked(true)
                .build();
    }

    private ChunkResult requestChunk(String url, long start, long end) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Range", "bytes=" + start + "-" + end);

            // Use byte array to avoid text decoding issues with binary firmware files
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new org.springframework.http.HttpEntity<>(headers),
                    byte[].class
            );

            String cacheStatus = response.getHeaders().getFirst(CF_CACHE_STATUS_HEADER);
            String cfRay = response.getHeaders().getFirst(CF_RAY_HEADER);
            boolean success = response.getStatusCode().is2xxSuccessful();
            String errorMessage = success
                    ? null
                    : "Chunk request returned HTTP status " + response.getStatusCode().value();

            return new ChunkResult(success, cacheStatus, cfRay, errorMessage);
        } catch (Exception e) {
            log.error("Failed to request chunk {}-{} for {}", start, end, url, e);
            return new ChunkResult(false, null, null, resolveExceptionMessage(e));
        }
    }

    private String resolveErrorMessage(Exception lastException, String lastFailureMessage) {
        if (lastException != null) {
            return resolveExceptionMessage(lastException);
        }
        return firstNonBlank(lastFailureMessage, "Unknown error");
    }

    private String resolveExceptionMessage(Exception exception) {
        return firstNonBlank(exception.getMessage(), exception.getClass().getSimpleName());
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private String extractPopFromCfRay(String cfRay) {
        if (cfRay == null || cfRay.isBlank()) {
            return null;
        }
        int index = cfRay.lastIndexOf('-');
        if (index < 0 || index == cfRay.length() - 1) {
            return null;
        }
        return cfRay.substring(index + 1).trim();
    }

    /**
     * Validate URL format and check for path traversal.
     * URLs must be absolute (http:// or https://).
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        // URL must be absolute (start with http:// or https://)
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            log.warn("URL must be absolute: {}", url);
            return false;
        }

        try {
            URI uri = new URI(url);

            // Check for path traversal attempts
            String path = uri.getPath();
            if (path != null && (path.contains("..") || path.contains("//"))) {
                log.warn("Potential path traversal detected in URL: {}", url);
                return false;
            }

            // Only allow http and https
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                log.warn("Invalid URL scheme: {}", scheme);
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            log.warn("Invalid URL syntax: {}", url, e);
            return false;
        }
    }

    private String buildFullUrl(String url) {
        // URL should already be complete (from fileTransferService.getDownloadUrl)
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        // Relative URLs should not happen, but just in case
        throw new IllegalArgumentException("URL must be absolute: " + url);
    }

    /**
     * Shutdown the executor service on bean destruction.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down CDN warm-up client executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Internal class to hold chunk request results.
     */
    private record ChunkResult(boolean success, String cacheStatus, String cfRay, String errorMessage) {}
}
