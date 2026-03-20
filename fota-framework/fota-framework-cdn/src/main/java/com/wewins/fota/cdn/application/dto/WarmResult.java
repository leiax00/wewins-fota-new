package com.wewins.fota.cdn.application.dto;

import com.wewins.fota.cdn.domain.cdn.model.enums.CdnWarmStrategy;
import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import lombok.Builder;
import lombok.Data;

/**
 * CDN warm-up result DTO.
 */
@Data
@Builder
public class WarmResult {

    /**
     * The URL that was warmed.
     */
    private String url;

    /**
     * Warm-up status.
     */
    private WarmStatus status;

    /**
     * Warm-up strategy that executed this request.
     */
    private CdnWarmStrategy strategy;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Cache status from CDN response header (e.g., HIT, MISS, EXPIRED).
     */
    private String cacheStatus;

    /**
     * Actual Cloudflare PoP that handled the warm request when available.
     */
    private String pop;

    /**
     * CF-RAY header value when available.
     */
    private String cfRay;

    /**
     * Whether the file was served in chunks.
     */
    private boolean chunked;

    /**
     * Raw response payload from remote warm executor when available.
     */
    private String rawResponse;
}
