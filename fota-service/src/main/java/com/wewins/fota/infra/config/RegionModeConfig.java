package com.wewins.fota.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 区域部署模式配置
 * <p>
 * 仅当 app.mode=region 时生效的配置
 * 提供区域模式专用的 Bean 定义
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "region")
public class RegionModeConfig {
    // 区域模式专用的 Bean 将在此定义
    // 例如：配置同步服务、数据转发服务等
}
