package com.wewins.fota.cdn.domain.cdn.model.enums;

/**
 * CDN warm-up status enumeration.
 */
public enum WarmStatus {

    /**
     * Pending warm-up.
     */
    PENDING,

    /**
     * Currently warming up.
     */
    WARMING,

    /**
     * Warm-up successful.
     */
    SUCCESS,

    /**
     * Warm-up failed.
     */
    FAILED
}
