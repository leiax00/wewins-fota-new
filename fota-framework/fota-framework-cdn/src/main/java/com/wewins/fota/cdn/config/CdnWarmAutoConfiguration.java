package com.wewins.fota.cdn.config;

import com.wewins.fota.cdn.application.service.CdnWarmService;
import com.wewins.fota.cdn.infra.client.CdnWarmClient;
import com.wewins.fota.cdn.infra.client.CdnWarmWorkerClient;
import com.wewins.fota.cdn.spi.CdnWorkerConfigProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
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
    @ConditionalOnBean(CdnWorkerConfigProvider.class)
    @ConditionalOnMissingBean
    public CdnWarmWorkerClient cdnWarmWorkerClient(
            CdnWorkerConfigProvider configProvider,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder
    ) {
        return new CdnWarmWorkerClient(configProvider, objectMapper, restClientBuilder);
    }

    @Bean
    @ConditionalOnMissingBean
    public CdnWarmService cdnWarmService(
            CdnWarmClient cdnWarmClient,
            ObjectProvider<CdnWarmWorkerClient> cdnWarmWorkerClientProvider,
            ObjectProvider<CdnWorkerConfigProvider> configProvider
    ) {
        return new CdnWarmService(
                cdnWarmClient,
                cdnWarmWorkerClientProvider.getIfAvailable(),
                configProvider.getIfAvailable()
        );
    }
}
