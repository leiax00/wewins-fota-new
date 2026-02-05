package com.wewins.fota.config;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * <p>
 * 配置拦截器、视图解析器等 Web 相关组件。
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-05
 */
@Configuration
@AllArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private UserContextCleanupInterceptor userContextCleanupInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userContextCleanupInterceptor)
                .addPathPatterns("/**");
    }
}
