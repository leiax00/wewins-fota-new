package com.wewins.fota.mq.core;

/**
 * Generic message publisher abstraction for business modules.
 */
public interface MqMessagePublisher {

    void publish(String destination, String payload);

    void publishJson(String destination, Object payload);
}
