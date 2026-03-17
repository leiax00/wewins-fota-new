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

/**
 * RabbitMQ 检查日志队列配置
 * <p>
 * 配置检查日志队列和对应的死信队列（DLQ）
 * </p>
 * <p>
 * 队列结构：
 * <ul>
 *   <li>主队列：fota.check.logs（处理检查日志）</li>
 *   <li>死信队列：fota.check.logs.dlq（存储失败消息）</li>
 *   <li>交换机：fota.check.logs.exchange</li>
 *   <li>DLQ 交换机：fota.check.logs.dlq.exchange</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-03-02
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RabbitMqCheckLogConfiguration.CheckLogsQueueProperties.class)
public class RabbitMqCheckLogConfiguration {

    /**
     * 检查日志队列属性
     */
    @Data
    @ConfigurationProperties(prefix = "app.mq.queues.check-logs")
    public static class CheckLogsQueueProperties {
        private String name = "fota.check.logs";
        private String dlqName = "fota.check.logs.dlq";
        private String exchange = "fota.check.logs.exchange";
        private String dlqExchange = "fota.check.logs.dlq.exchange";
        private boolean durable = true;
        private int ttl = 86400000; // 24 小时（毫秒）
        private int maxLength = 100000; // DLQ 最大长度

        // 批量消费配置
        private int batchSize = 100;           // 批量大小
        private long receiveTimeoutMs = 5000;  // 接收超时（毫秒）
    }

    /**
     * 死信队列配置
     * <p>
     * x-dead-letter-exchange: 死信交换机
     * x-dead-letter-routing-key: 死信路由键
     * </p>
     */
    @Bean
    public Queue checkLogsDlq(CheckLogsQueueProperties properties) {
        return QueueBuilder.durable(properties.getDlqName())
                .withArgument("x-dead-letter-exchange", properties.getExchange())
                .withArgument("x-dead-letter-routing-key", properties.getName())
                .withArgument("x-message-ttl", properties.getTtl())
                .withArgument("x-max-length", properties.getMaxLength())
                .build();
    }

    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange checkLogsDlqExchange(CheckLogsQueueProperties properties) {
        return new DirectExchange(properties.getDlqExchange(), true, false);
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding checkLogsDlqBinding(
            Queue checkLogsDlq,
            DirectExchange checkLogsDlqExchange,
            CheckLogsQueueProperties properties) {
        return BindingBuilder.bind(checkLogsDlq)
                .to(checkLogsDlqExchange)
                .with(properties.getDlqName());
    }

    /**
     * 主队列（检查日志队列）
     * <p>
     * 配置死信路由，失败消息自动进入 DLQ
     * </p>
     */
    @Bean
    public Queue checkLogsQueue(CheckLogsQueueProperties properties) {
        return QueueBuilder.durable(properties.getName())
                .withArgument("x-dead-letter-exchange", properties.getDlqExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDlqName())
                .build();
    }

    /**
     * 主交换机
     */
    @Bean
    public DirectExchange checkLogsExchange(CheckLogsQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    /**
     * 主队列绑定
     */
    @Bean
    public Binding checkLogsBinding(
            Queue checkLogsQueue,
            DirectExchange checkLogsExchange,
            CheckLogsQueueProperties properties) {
        return BindingBuilder.bind(checkLogsQueue)
                .to(checkLogsExchange)
                .with(properties.getName());
    }

    /**
     * 检查日志批量监听器容器工厂
     * <p>
     * 配置批量接收消息的监听器
     * </p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory checkLogBatchRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            CheckLogsQueueProperties properties) {

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
