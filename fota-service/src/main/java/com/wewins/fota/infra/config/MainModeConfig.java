package com.wewins.fota.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 主区域模式配置
 * <p>
 * 仅当 app.mode=main 时生效的配置
 * 提供主区域专用的 Bean 定义
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "main")
public class MainModeConfig {
    // 主区域专用的 Bean 将在此定义
    // 例如：跨区域配置汇总服务、内部 API 等
}
