package com.wewins.fota.adapter.api.admin.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheEvictResultDTO {
    int evictedScopes;
    int evictedDeviceCount;
    String message;
}
