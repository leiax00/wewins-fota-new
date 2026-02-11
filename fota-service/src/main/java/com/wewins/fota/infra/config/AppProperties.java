package com.wewins.fota.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用配置属性
 * <p>
 * 绑定 application.yml 中的 app 配置前缀
 * 提供强类型访问配置
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * 运行模式
     * <p>
     * 可选值：main（主区域）、region（区域部署）
     * </p>
     */
    private String mode = "main";

    /**
     * 主区域配置（仅 mode=main 时有效）
     */
    private MainProperties main;

    /**
     * 集群配置
     * <p>
     * 默认启用集群模式支持
     * </p>
     */
    private ClusterProperties cluster = new ClusterProperties();
}
