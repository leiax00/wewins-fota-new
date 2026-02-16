package com.wewins.fota.domain.reporting.model;

import lombok.Builder;
import lombok.Value;

/**
 * 设备升级上报领域模型
 */
@Value
@Builder
public class UpgradeReport {
    String imei;
    String currentVersion;
    String targetVersion;
    String eventType;
    String downloadUrl;
    String clientIp;
    String userAgent;
    String ext;
}
