package com.wewins.fota.mq.config;

import lombok.Data;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RabbitMqDeviceInfoUpdateConfiguration.DeviceInfoUpdateQueueProperties.class)
public class RabbitMqDeviceInfoUpdateConfiguration {

    @Data
    @ConfigurationProperties(prefix = "app.mq.queues.device-info-update")
    public static class DeviceInfoUpdateQueueProperties {
        private String name = "fota.device.info.update";
        private String dlqName = "fota.device.info.update.dlq";
        private String exchange = "fota.device.info.update.exchange";
        private String dlqExchange = "fota.device.info.update.dlq.exchange";
        private boolean durable = true;
        private int ttl = 24 * 60 * 60 * 1000;
        private int maxLength = 50000;
        private int batchSize = 500;
        private long receiveTimeoutMs = 30 * 1000;
    }

    @Bean
    public Queue deviceInfoUpdateDlq(DeviceInfoUpdateQueueProperties properties) {
        return QueueBuilder.durable(properties.getDlqName())
                .withArgument("x-dead-letter-exchange", properties.getExchange())
                .withArgument("x-dead-letter-routing-key", properties.getName())
                .withArgument("x-message-ttl", properties.getTtl())
                .withArgument("x-max-length", properties.getMaxLength())
                .build();
    }

    @Bean
    public DirectExchange deviceInfoUpdateDlqExchange(DeviceInfoUpdateQueueProperties properties) {
        return new DirectExchange(properties.getDlqExchange(), true, false);
    }

    @Bean
    public Binding deviceInfoUpdateDlqBinding(
            Queue deviceInfoUpdateDlq,
            DirectExchange deviceInfoUpdateDlqExchange,
            DeviceInfoUpdateQueueProperties properties) {
        return BindingBuilder.bind(deviceInfoUpdateDlq)
                .to(deviceInfoUpdateDlqExchange)
                .with(properties.getDlqName());
    }

    @Bean
    public Queue deviceInfoUpdateQueue(DeviceInfoUpdateQueueProperties properties) {
        return QueueBuilder.durable(properties.getName())
                .withArgument("x-dead-letter-exchange", properties.getDlqExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDlqName())
                .build();
    }

    @Bean
    public DirectExchange deviceInfoUpdateExchange(DeviceInfoUpdateQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Binding deviceInfoUpdateBinding(
            Queue deviceInfoUpdateQueue,
            DirectExchange deviceInfoUpdateExchange,
            DeviceInfoUpdateQueueProperties properties) {
        return BindingBuilder.bind(deviceInfoUpdateQueue)
                .to(deviceInfoUpdateExchange)
                .with(properties.getName());
    }

    @Bean
    public SimpleRabbitListenerContainerFactory deviceInfoUpdateBatchRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            DeviceInfoUpdateQueueProperties properties) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setBatchListener(true);
        factory.setBatchSize(properties.getBatchSize());
        factory.setConsumerBatchEnabled(true);
        factory.setReceiveTimeout(properties.getReceiveTimeoutMs());

        return factory;
    }
}
