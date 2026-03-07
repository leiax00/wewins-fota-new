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
 * RabbitMQ 上报事件队列配置
 * <p>
 * 配置升级事件队列和对应的死信队列（DLQ）
 * </p>
 * <p>
 * 队列结构：
 * <ul>
 *   <li>主队列：fota.upgrade.events（处理升级事件）</li>
 *   <li>死信队列：fota.upgrade.events.dlq（存储失败消息）</li>
 *   <li>交换机：fota.upgrade.events.exchange</li>
 *   <li>DLQ 交换机：fota.upgrade.events.dlq.exchange</li>
 * </ul>
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-28
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RabbitMqReportConfiguration.UpgradeEventsQueueProperties.class)
public class RabbitMqReportConfiguration {

    /**
     * 升级事件队列属性
     */
    @Data
    @ConfigurationProperties(prefix = "app.mq.queues.upgrade-events")
    public static class UpgradeEventsQueueProperties {
        private String name = "fota.upgrade.events";
        private String dlqName = "fota.upgrade.events.dlq";
        private String exchange = "fota.upgrade.events.exchange";
        private String dlqExchange = "fota.upgrade.events.dlq.exchange";
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
    public Queue upgradeEventsDlq(UpgradeEventsQueueProperties properties) {
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
    public DirectExchange upgradeEventsDlqExchange(UpgradeEventsQueueProperties properties) {
        return new DirectExchange(properties.getDlqExchange(), true, false);
    }

    /**
     * 死信队列绑定
     */
    @Bean
    public Binding upgradeEventsDlqBinding(
            Queue upgradeEventsDlq,
            DirectExchange upgradeEventsDlqExchange,
            UpgradeEventsQueueProperties properties) {
        return BindingBuilder.bind(upgradeEventsDlq)
                .to(upgradeEventsDlqExchange)
                .with(properties.getDlqName());
    }

    /**
     * 主队列（升级事件队列）
     * <p>
     * 配置死信路由，失败消息自动进入 DLQ
     * </p>
     */
    @Bean
    public Queue upgradeEventsQueue(UpgradeEventsQueueProperties properties) {
        return QueueBuilder.durable(properties.getName())
                .withArgument("x-dead-letter-exchange", properties.getDlqExchange())
                .withArgument("x-dead-letter-routing-key", properties.getDlqName())
                .build();
    }

    /**
     * 主交换机
     */
    @Bean
    public DirectExchange upgradeEventsExchange(UpgradeEventsQueueProperties properties) {
        return new DirectExchange(properties.getExchange(), true, false);
    }

    /**
     * 主队列绑定
     */
    @Bean
    public Binding upgradeEventsBinding(
            Queue upgradeEventsQueue,
            DirectExchange upgradeEventsExchange,
            UpgradeEventsQueueProperties properties) {
        return BindingBuilder.bind(upgradeEventsQueue)
                .to(upgradeEventsExchange)
                .with(properties.getName());
    }

    /**
     * 批量监听器容器工厂
     * <p>
     * 配置批量接收消息的监听器
     * </p>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory batchRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            UpgradeEventsQueueProperties properties) {

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
