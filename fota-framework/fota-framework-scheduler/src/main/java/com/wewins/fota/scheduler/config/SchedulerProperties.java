package com.wewins.fota.scheduler.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 调度器模块配置属性。
 *
 * @author FOTA Team
 * @since 2026-04-16
 */
@Data
@ConfigurationProperties(prefix = "app.scheduler")
public class SchedulerProperties {

    /**
     * 调度器是否启用
     */
    private boolean enabled = true;
}
