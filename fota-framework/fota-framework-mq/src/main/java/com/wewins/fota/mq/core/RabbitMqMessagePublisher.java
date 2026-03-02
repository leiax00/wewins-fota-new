package com.wewins.fota.mq.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ implementation of framework-level message publisher.
 */
@RequiredArgsConstructor
public class RabbitMqMessagePublisher implements MqMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String destination, String payload) {
        rabbitTemplate.convertAndSend(destination, payload);
    }

    @Override
    public void publishJson(String destination, Object payload) {
        // 直接发送对象，让 RabbitTemplate 的 MessageConverter 处理序列化
        // 避免 double serialization（先手动序列化为字符串，再由 MessageConverter 序列化）
        rabbitTemplate.convertAndSend(destination, payload);
    }
}
