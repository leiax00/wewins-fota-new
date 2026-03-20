package com.wewins.fota.cdn.application.dto;

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
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Cache status from CDN response header (e.g., HIT, MISS, EXPIRED).
     */
    private String cacheStatus;

    /**
     * Whether the file was served in chunks.
     */
    private boolean chunked;
}
