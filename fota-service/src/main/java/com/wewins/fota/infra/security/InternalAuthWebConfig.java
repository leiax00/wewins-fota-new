package com.wewins.fota.infra.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部接口鉴权配置
 *
 * @author FOTA Team
 * @since 2026-02-12
 */
@Configuration
@ConditionalOnProperty(name = "app.mode", havingValue = "main")
@ConditionalOnProperty(prefix = "app.internal-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InternalAuthWebConfig implements WebMvcConfigurer {

    private final InternalHmacInterceptor internalHmacInterceptor;

    public InternalAuthWebConfig(InternalHmacInterceptor internalHmacInterceptor) {
        this.internalHmacInterceptor = internalHmacInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalHmacInterceptor)
                .addPathPatterns("/internal/**");
    }
}
