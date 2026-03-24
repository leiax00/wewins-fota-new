package com.wewins.fota.infra.gateway;

import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.wewins.fota.domain.device.service.DeviceInfoUpdateGateway;
import com.wewins.fota.mq.core.MqMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RabbitMqDeviceInfoUpdateGateway implements DeviceInfoUpdateGateway {

    private final MqMessagePublisher mqMessagePublisher;

    @Value("${app.mq.queues.device-info-update.name:fota.device.info.update}")
    private String deviceInfoUpdateQueue;

    @Override
    public void accept(DeviceInfoUpdateMessage message) {
        if (message == null) {
            log.warn("设备信息更新消息为空，跳过投递");
            return;
        }

        try {
            mqMessagePublisher.publishJson(deviceInfoUpdateQueue, message);
            log.debug("设备信息更新消息已投递到 MQ: imei={}", message.getImei());
        } catch (Exception e) {
            log.error("设备信息更新消息发送失败: imei={}", message.getImei(), e);
            throw new IllegalStateException("设备信息更新消息发送失败", e);
        }
    }
}
