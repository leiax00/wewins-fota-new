package com.wewins.fota.infra.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wewins.fota.analytics.entity.DeviceCheckLog;
import com.wewins.fota.analytics.entity.DeviceUpgradeEvent;
import com.wewins.fota.analytics.service.DeviceUpgradeEventService;
import com.rabbitmq.client.Channel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 升级事件消费者
 * <p>
 * 从 RabbitMQ 接收设备上报事件，批量写入 ClickHouse
 * 使用手动 ACK，失败时拒绝并进入 DLQ
 * </p>
 *
 * @author FOTA Team
 * @since 2026-02-11
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UpgradeEventConsumer {

    private final ObjectMapper objectMapper;
    private final DeviceUpgradeEventService deviceUpgradeEventService;

    /**
     * 监听升级事件队列
     * <p>
     * 队列名称：通过配置 app.mq.upgrade-event-queue 指定，默认为 fota.upgrade.events
     * </p>
     *
     * @param payload 消息体（JSON 字符串）
     * @param message RabbitMQ 消息对象
     * @param channel RabbitMQ 通道
     * @throws IOException 消息处理失败时抛出
     */
    @RabbitListener(queues = "${app.mq.upgrade-event-queue:fota.upgrade.events}")
    public void onUpgradeEvent(String payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            UpgradeEventMessage eventMessage = parseMessage(payload);

            // 记录检查日志（如果有）
            if (eventMessage.getCheckLog() != null) {
                deviceUpgradeEventService.recordCheckLog(eventMessage.getCheckLog());
            }

            // 批量记录升级事件
            if (eventMessage.getEvents() != null && !eventMessage.getEvents().isEmpty()) {
                deviceUpgradeEventService.recordUpgradeEvents(eventMessage.getEvents());
            }

            // 手动 ACK
            channel.basicAck(deliveryTag, false);
            log.debug("升级事件处理成功: deliveryTag={}, eventCount={}", deliveryTag,
                    eventMessage.getEvents() != null ? eventMessage.getEvents().size() : 0);

        } catch (Exception ex) {
            log.error("升级事件处理失败，拒绝并进入 DLQ: deliveryTag={}", deliveryTag, ex);
            // 拒绝消息，不重新入队，进入 DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 解析消息体
     *
     * @param payload JSON 字符串
     * @return UpgradeEventMessage 对象
     */
    private UpgradeEventMessage parseMessage(String payload) {
        try {
            return objectMapper.readValue(payload, UpgradeEventMessage.class);
        } catch (Exception e) {
            log.error("消息解析失败: payload={}", payload, e);
            throw new RuntimeException("消息解析失败", e);
        }
    }

    /**
     * 升级事件消息 DTO
     */
    @Data
    public static class UpgradeEventMessage {
        /**
         * 检查日志（可选）
         */
        private DeviceCheckLog checkLog;

        /**
         * 升级事件列表
         */
        private List<DeviceUpgradeEvent> events;
    }
}
