package com.wewins.fota.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务模块配置属性。
 *
 * @author FOTA Team
 * @since 2026-03-19
 */
@Data
@ConfigurationProperties(prefix = "app.task")
public class TaskProperties {

    /**
     * SSE 连接超时时间（毫秒）
     */
    private long sseTimeout = 300000L;

    /**
     * 任务默认超时时间（毫秒）
     */
    private long taskTimeout = 3600000L;

    /**
     * SSE 最大连接数
     */
    private int maxSseConnections = 1000;
}
