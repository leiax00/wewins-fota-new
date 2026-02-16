package com.wewins.fota.cache.cluster;

import lombok.Data;

/**
 * Node registry instance metadata.
 */
@Data
public class NodeInstance {
    private String code;
    private String name;
    private String baseUrl;
    private String timeZone;
    private String mode;
    private String instanceId;
    private String status;
    private long registeredAt;
    private long lastHeartbeatAt;
}
