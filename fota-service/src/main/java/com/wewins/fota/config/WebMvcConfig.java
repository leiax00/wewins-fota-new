package com.wewins.fota.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 用于配置拦截器、视图解析器、CORS 等 Web 相关组件。
 * </p>
 * <p>
 * 注意：
 * <ul>
 *   <li>用户上下文清理已在 {@link com.wewins.fota.web.jwt.JwtAuthenticationFilter} 中处理</li>
 *   <li>安全配置在 {@link com.wewins.fota.web.config.SecurityConfig} 中处理</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // 当前无额外 Web MVC 配置
    // 可在此添加拦截器、CORS、视图解析器等配置
}

