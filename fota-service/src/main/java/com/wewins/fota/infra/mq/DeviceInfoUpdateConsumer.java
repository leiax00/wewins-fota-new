package com.wewins.fota.infra.mq;

import com.wewins.fota.application.device.DeviceInfoUpdateAppService;
import com.wewins.fota.domain.device.model.aggregate.DeviceInfoUpdateMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DeviceInfoUpdateConsumer {

    private final MessageConverter messageConverter;
    private final DeviceInfoUpdateAppService deviceInfoUpdateAppService;

    @RabbitListener(
            queues = "${app.mq.queues.device-info-update.name:fota.device.info.update}",
            containerFactory = "deviceInfoUpdateBatchRabbitListenerContainerFactory"
    )
    public void onDeviceInfoUpdates(List<Message> messages, Channel channel) throws IOException {
        if (messages.isEmpty()) {
            log.warn("批量消息列表为空，跳过处理");
            return;
        }

        long lastDeliveryTag = messages.getLast().getMessageProperties().getDeliveryTag();

        try {
            List<DeviceInfoUpdateMessage> updateMessages = parseMessages(messages);
            if (updateMessages.isEmpty()) {
                log.warn("所有消息解析失败，ACK 并跳过: deliveryTag={}", lastDeliveryTag);
                channel.basicAck(lastDeliveryTag, true);
                return;
            }

            deviceInfoUpdateAppService.processBatch(updateMessages);

            channel.basicAck(lastDeliveryTag, true);
            log.debug("设备信息更新批量处理成功: count={}", updateMessages.size());

        } catch (Exception ex) {
            log.error("设备信息更新批量处理失败，拒绝并进入 DLQ: count={}", messages.size(), ex);
            channel.basicNack(lastDeliveryTag, true, false);
        }
    }

    private List<DeviceInfoUpdateMessage> parseMessages(List<Message> messages) {
        List<DeviceInfoUpdateMessage> updateMessages = new ArrayList<>();
        for (Message message : messages) {
            try {
                Object payload = messageConverter.fromMessage(message);
                if (payload instanceof DeviceInfoUpdateMessage updateMessage) {
                    updateMessages.add(updateMessage);
                } else {
                    log.warn("消息类型不匹配: expected=DeviceInfoUpdateMessage, actual={}",
                            payload.getClass().getName());
                }
            } catch (Exception e) {
                log.error("设备信息更新消息解析失败: payload={}", new String(message.getBody()), e);
            }
        }
        return updateMessages;
    }
}
