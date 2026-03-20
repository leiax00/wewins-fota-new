package com.wewins.fota.cdn.config;

import com.wewins.fota.cdn.application.service.CdnWarmService;
import com.wewins.fota.cdn.infra.client.CdnWarmClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * CDN warm-up auto configuration.
 */
@AutoConfiguration
@EnableConfigurationProperties(CdnWarmProperties.class)
@ConditionalOnProperty(prefix = "app.cdn.warm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CdnWarmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "cdnWarmRestTemplate")
    public RestTemplate cdnWarmRestTemplate(CdnWarmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Set connection timeout
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        // Set read timeout based on configured timeout
        factory.setReadTimeout((int) Duration.ofSeconds(properties.getTimeoutSeconds()).toMillis());
        return new RestTemplate(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public CdnWarmClient cdnWarmClient(
            CdnWarmProperties properties,
            RestTemplate cdnWarmRestTemplate
    ) {
        return new CdnWarmClient(properties, cdnWarmRestTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    public CdnWarmService cdnWarmService(CdnWarmClient cdnWarmClient) {
        return new CdnWarmService(cdnWarmClient);
    }
}
