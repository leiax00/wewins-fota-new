package com.wewins.fota.infra.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.node")
public class NodeProperties {

    private String code = "main";

    private String name = "主区域";

    private String baseUrl = "https://main.api.xxx.com";

    private String timeZone = "Asia/Shanghai";
}
