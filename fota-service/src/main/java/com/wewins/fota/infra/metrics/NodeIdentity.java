package com.wewins.fota.infra.metrics;

import com.wewins.fota.infra.config.properties.NodeProperties;
import org.springframework.stereotype.Component;

/**
 * 从 app.node.code 派生监控与展示使用的节点身份。
 *
 * <p>约定格式：region-host-instance，例如 main-lax-svc。</p>
 */
@Component
public class NodeIdentity {

    private final String nodeCode;

    public NodeIdentity(NodeProperties nodeProperties) {
        this.nodeCode = normalize(nodeProperties.getCode(), "main");
    }

    public String nodeCode() {
        return nodeCode;
    }

    public String regionCode() {
        String[] parts = split();
        return parts.length >= 1 && !parts[0].isBlank() ? parts[0] : "main";
    }

    public String hostCode() {
        String[] parts = split();
        return parts.length >= 2 && !parts[1].isBlank() ? parts[1] : nodeCode;
    }

    public String nodeId() {
        return nodeCode;
    }

    public String monitoringInstanceLabel() {
        return nodeCode;
    }

    private String[] split() {
        return nodeCode == null ? new String[0] : nodeCode.split("-");
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
