package com.wewins.fota.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.mq.core.MqMessagePublisher;
import com.wewins.fota.mq.core.RabbitMqMessagePublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Common RabbitMQ auto configuration for framework starter.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(MqProperties.class)
public class RabbitMqFrameworkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageConverter.class)
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(MqMessagePublisher.class)
    public MqMessagePublisher mqMessagePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            MqProperties mqProperties
    ) {
        rabbitTemplate.setMandatory(mqProperties.getPublish().isMandatory());
        return new RabbitMqMessagePublisher(rabbitTemplate, objectMapper);
    }
}
