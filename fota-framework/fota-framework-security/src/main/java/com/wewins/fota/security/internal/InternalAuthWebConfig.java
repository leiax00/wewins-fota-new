package com.wewins.fota.security.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Internal API auth web configuration.
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
        registry.addInterceptor(internalHmacInterceptor).addPathPatterns("/internal/**");
    }
}
