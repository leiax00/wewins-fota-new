package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class CacheEvictPreviewDTO {
    int estimatedScopes;
    int estimatedDeviceCount;
    List<String> affectedTargets;
    boolean executable;
    String message;
}
