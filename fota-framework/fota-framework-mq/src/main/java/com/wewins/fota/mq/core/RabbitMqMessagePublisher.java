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
        try {
            publish(destination, objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("Serialize MQ payload failed", ex);
        }
    }
}
