package com.wewins.fota.cdn.application.dto;

import lombok.Data;

import java.util.List;

/**
 * CDN warm-up request DTO.
 */
@Data
public class WarmRequest {

    /**
     * List of URLs to warm up.
     */
    private List<String> urls;

    /**
     * Whether to force warm-up even if cached.
     */
    private boolean forceWarm = false;
}
