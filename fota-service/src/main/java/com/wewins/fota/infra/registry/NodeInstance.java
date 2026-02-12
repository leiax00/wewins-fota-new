package com.wewins.fota.infra.registry;

import lombok.Data;

/**
 * 节点实例注册信息
 *
 * @author FOTA Team
 * @since 2026-02-12
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
