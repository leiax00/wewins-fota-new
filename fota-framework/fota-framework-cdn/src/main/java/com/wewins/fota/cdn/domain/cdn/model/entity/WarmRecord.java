package com.wewins.fota.cdn.domain.cdn.model.entity;

import com.wewins.fota.cdn.domain.cdn.model.enums.WarmStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CDN warm-up record entity.
 */
@Data
public class WarmRecord {

    /**
     * Unique identifier.
     */
    private Long id;

    /**
     * File URL or key to warm up.
     */
    private String url;

    /**
     * Current warm-up status.
     */
    private WarmStatus status;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Creation time.
     */
    private LocalDateTime createdAt;

    /**
     * Last update time.
     */
    private LocalDateTime updatedAt;

    /**
     * Number of retry attempts.
     */
    private int retryCount;
}
