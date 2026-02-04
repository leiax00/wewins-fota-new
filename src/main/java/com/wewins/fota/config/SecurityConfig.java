package com.wewins.fota.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置（M1 简化版）
 *
 * <p>Milestone 1 阶段：只启用基础安全配置，不实现完整 RBAC
 * <ul>
 *   <li>允许 Actuator 端点无需认证</li>
 *   <li>设备 API（/v1/upgrade/*）无需认证（后续添加）</li>
 *   <li>管理 API（/api/v1/admin/*）需要认证（M4 实现）</li>
 * </ul>
 *
 * @see <a href="https://docs/tasks.md#20">任务 #20：Spring Security + JWT 认证配置</a>
 * @since 0.1.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链
     *
     * @param http HttpSecurity 配置构建器
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（暂时）
            .csrf(AbstractHttpConfigurer::disable)

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // Actuator 健康检查端点：无需认证
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // 其他请求：暂时允许所有（M1 阶段）
                // M4 之后再添加完整的授权规则
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
